package ru.descend.shared.http

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.ClientSession
import io.ktor.http.*
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*
import org.bson.conversions.Bson
import ru.descend.features.character.model.Character
import ru.descend.features.user.model.User
import ru.descend.infrastructure.http.AppJson
import ru.descend.infrastructure.mongo.BaseRepository
import ru.descend.infrastructure.mongo.MongoFactory.transactionExecute
import ru.descend.infrastructure.security.Actor
import ru.descend.infrastructure.security.actor
import ru.descend.shared.http.commands.*
import ru.descend.shared.error.BaseException
import ru.descend.shared.model.StockEntity
import ru.descend.shared.model.VersionedEntity

interface RouteRegistrar { fun register(routing: Routing) }

/** Authenticated facade: scoped reads, explicit editable fields, typed decoding and client CAS. */
abstract class BaseRoute<T : StockEntity, R>(
    protected val repository: BaseRepository<T>,
    val entitySerializer: KSerializer<T>,
    val responseSerializer: KSerializer<R>,
    private val toResponse: (T) -> R
) : RouteRegistrar {
    private val kind = entitySerializer.descriptor.serialName.substringAfterLast(".").lowercase()
    private val basePath = "/api/v1/$kind"
    private val editable: Set<String> get() = when (kind) {
        "user" -> setOf("name", "email", "age")
        "character" -> setOf("name", "description")
        "equipment" -> setOf("name", "description", "image", "slot", "rarity", "itemLevel", "weaponType", "damage_min", "damage_max", "attackSpeed", "durability", "defense", "price", "poeBaseId", "modifierDefinitionRefs", "stockModifierDefinitionRefs")
        "items" -> setOf("name", "category", "subCategory", "description", "image", "price", "poeBaseId")
        "recipe" -> setOf("name", "arrayIn", "arrayOut", "requirement", "timeWork", "needOpenRecipe")
        "redemptioncodes" -> setOf("code", "treasure", "description", "expiredAt")
        else -> emptySet()
    }
    override fun register(routing: Routing) {
        routing.authenticate("jwt-auth") {
            route(basePath) {
                additionalRoutes(this)
                getAllRoute(this)
                get("/paged") {
                    val actor = call.actor(); val page = call.queryParam("page", 0); val size = call.queryParam("size", 20)
                    if (page !in 0..100000 || size !in 1..100) invalid("Invalid pagination")
                    val filter = Filters.and(scope(actor), CatalogQuery.filter(call.request.queryParameters, kind))
                    val data = repository.collection.find(filter).sort(Sorts.ascending("_id")).skip(page * size).limit(size).toList()
                    val total = repository.collection.countDocuments(filter)
                    call.respondJson(ApiMongoResponse.serializer(PagedMongoResponse.serializer(responseSerializer)), ApiMongoResponse.ok(PagedMongoResponse(data.map(toResponse), page, size, total, ((total + size - 1) / size).toInt())))
                }
                get("/count") { call.respond(ApiMongoResponse.ok(mapOf("count" to repository.collection.countDocuments(Filters.and(scope(call.actor()), CatalogQuery.filter(call.request.queryParameters, kind)))))) }
                post {
                    val actor = call.actor()
                    if (kind != "character") actor.requireAdmin()
                    val input = call.receiveCommand<JsonArray>()
                    if (input.isEmpty() || input.size > 50) invalid("Expected 1 to 50 objects")
                    val entities = input.map { value ->
                        val obj = value as? JsonObject ?: invalid("Expected object")
                        val entity: T = when (kind) {
                            "character" -> {
                                val dto = decode(CreateCharacterCommand.serializer(), obj)
                                @Suppress("UNCHECKED_CAST")
                                (Character(actor.id, dto.name, dto.description) as T)
                            }
                            "user" -> {
                                val dto = decode(CreateUserCommand.serializer(), obj)
                                @Suppress("UNCHECKED_CAST")
                                (User(name = dto.name, email = dto.email, login = dto.login, password = dto.password, age = dto.age) as T)
                            }
                            else -> {
                                if ((obj.keys - editable - setOf("type")).isNotEmpty()) invalid("Field is not creatable")
                                decode(entitySerializer, obj)
                            }
                        }
                        validate(entity)
                        entity
                    }
                    val created = transactionExecute("api.$kind.create") { repository.insertMany(entities, it) }
                    call.respondJson(ApiMongoResponse.serializer(ListSerializer(responseSerializer)), ApiMongoResponse.ok(created.map(toResponse)))
                }
                put {
                    val actor = call.actor(); val id = call.idParam(); val command = call.receiveCommand<UpdateCommand>()
                    if (kind !in setOf("user", "character")) actor.requireAdmin()
                    if ("poeBaseId" in command.changes) invalid("Base binding is immutable")
                    if (command.changes.isEmpty() || (command.changes.keys - editable).isNotEmpty()) invalid("Field is not editable")
                    val result = transactionExecute("api.$kind.update") { session ->
                        val old = authorized(id, actor, session)
                        val versioned = old as? VersionedEntity ?: invalid("Entity does not support revisions")
                        checkVersion(versioned.version, command.expectedVersion)
                        val json = AppJson.encodeToJsonElement(entitySerializer, old).jsonObject
                        val next = decode(entitySerializer, JsonObject(json + command.changes))
                        validate(next)
                        repository.validateApiUpdate(command.changes.mapValues { (_, v) -> native(v) })
                        repository.update(next, session)
                        next
                    }
                    call.respondJson(ApiMongoResponse.serializer(responseSerializer), ApiMongoResponse.ok(toResponse(result)))
                }
                delete {
                    val actor = call.actor(); val id = call.idParam(); val command = call.receiveCommand<DeleteCommand>()
                    if (kind != "character") actor.requireAdmin()
                    if (kind == "user" && id == actor.id) invalid("Administrators cannot delete their own account")
                    transactionExecute("api.$kind.delete") { session ->
                        val entity = authorized(id, actor, session) as? VersionedEntity ?: invalid("Entity does not support revisions")
                        checkVersion(entity.version, command.expectedVersion)
                        // Soft deletion keeps references and prevents an old version matching a recreated ID.
                        entity.deleted = true
                        @Suppress("UNCHECKED_CAST")
                        repository.update(entity as T, session)
                        if (entity is Character) {
                            val chars = repository as ru.descend.features.character.persistence.CharacterRepository
                            val owner = chars.userRepository.findById(entity.userId, session) ?: missing()
                            owner.countCharacters = (owner.countCharacters - 1).coerceAtLeast(0)
                            chars.userRepository.update(owner, session)
                        }
                    }
                    call.respond(ApiMongoResponse.ok("Deleted"))
                }
            }
        }
    }
    private fun scope(actor: Actor): Bson {
        if (kind == "redemptioncodes") actor.requireAdmin()
        val own = when {
            actor.admin -> Filters.empty()
            kind == "user" -> Filters.eq("_id", actor.id)
            kind == "character" -> Filters.eq("userId", actor.id)
            else -> Filters.empty()
        }
        return Filters.and(Filters.ne("deleted", true), own)
    }
    private suspend fun authorized(id: String, actor: Actor, session: ClientSession? = null): T {
        val filter = Filters.and(scope(actor), Filters.eq("_id", id))
        val rows = if (session == null) repository.collection.find(filter) else repository.collection.find(session, filter)
        return rows.limit(1).toList().firstOrNull() ?: missing()
    }
    open fun getAllRoute(route: Route): Route = route.get {
        val actor = call.actor(); val id = call.request.queryParameters["id"]
        if (id != null) {
            val item = authorized(checkedId(id), actor)
            call.respondJson(ApiMongoResponse.serializer(responseSerializer), ApiMongoResponse.ok(toResponse(item)))
        } else {
            val list = repository.collection.find(scope(actor)).sort(Sorts.ascending("_id")).limit(100).toList()
            call.respondJson(ApiMongoResponse.serializer(ListSerializer(responseSerializer)), ApiMongoResponse.ok(list.map(toResponse)))
        }
    }
    private fun native(value: JsonElement): Any? = when (value) {
        JsonNull -> null
        is JsonObject -> value.mapValues { native(it.value) }
        is JsonArray -> value.map(::native)
        is JsonPrimitive -> if (value.isString) value.content else value.booleanOrNull ?: value.longOrNull ?: value.doubleOrNull
    }
    private fun validate(entity: T) {
        val obj = AppJson.encodeToJsonElement(entitySerializer, entity).jsonObject
        obj["name"]?.jsonPrimitive?.content?.let { if (it.isBlank() || it.length > 100) invalid("Name must contain 1 to 100 characters") }
        obj["description"]?.jsonPrimitive?.contentOrNull?.let { if (it.length > 2000) invalid("Description is too long") }
        for (key in listOf("price", "damage_min", "damage_max", "attackSpeed", "durability", "defense")) {
            obj[key]?.jsonPrimitive?.doubleOrNull?.let { if (!it.isFinite() || it < 0) invalid("Invalid numeric property") }
        }
        if (obj["itemLevel"]?.jsonPrimitive?.intOrNull?.let { it !in 1..100 } == true) invalid("Invalid item level")
    }
    private fun <V> decode(serializer: KSerializer<V>, value: JsonObject): V = try { CommandJson.decodeFromJsonElement(serializer, value) } catch (e: kotlinx.serialization.SerializationException) { invalid("Invalid fields or types") }
    protected open fun additionalRoutes(route: Route): Route = route
    protected fun ApplicationCall.queryParam(name: String): String = request.queryParameters[name] ?: invalid("Missing parameter: $name")
    inline fun <reified E> ApplicationCall.queryParam(name: String, default: E): E {
        val value = request.queryParameters[name] ?: return default
        return when (E::class) { Int::class -> (value.toIntOrNull() ?: invalid("Invalid $name")) as E; String::class -> value as E; else -> invalid("Unsupported query type") }
    }
    protected fun ApplicationCall.idParam() = checkedId(request.queryParameters["id"])
    private suspend fun <V> ApplicationCall.respondJson(serializer: KSerializer<V>, value: V) = respondText(AppJson.encodeToString(serializer, value), ContentType.Application.Json)
}

@Serializable
@kotlinx.serialization.SerialName("base.route.ApiMongoResponse")
data class ApiMongoResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: BaseException? = null
) {
    companion object {
        fun <T> ok(data: T?, message: String? = null) =
            ApiMongoResponse(success = true, data = data)

        fun error(exception: BaseException) =
            ApiMongoResponse(success = false, data = null, error = exception)
    }
}

@Serializable
@kotlinx.serialization.SerialName("base.route.PagedMongoResponse")
data class PagedMongoResponse<T>(
    val items: List<T>,
    val page: Int,
    val pageSize: Int,
    val totalItems: Long,
    val totalPages: Int
)

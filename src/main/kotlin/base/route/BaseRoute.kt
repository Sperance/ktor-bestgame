package base.route

import CONST_API_VERSION
import CONST_PAGE_SIZE_DEFAULT
import CONST_SYSTEM_FIELDS
import base.entity.StockEntity
import base.exception.BaseException
import base.exception.BaseRouteExceptions
import base.repository.BaseRepository
import base.repository.EntityCache
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import java.util.concurrent.atomic.AtomicReference
import config.MongoFactory.transactionExecute
import extensions.saveChildren
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import server.addons.AppJson

interface RouteRegistrar {
    fun register(routing: Routing)
}

/** Корень маршрутов коллекции: `/api/v1/<collection>`. */
fun apiPath(collection: String): String = "/api/v$CONST_API_VERSION/$collection"

/**
 * Операции общего CRUD. Коллекция открывает только те, которые клиент действительно зовёт:
 * всё остальное - лишняя поверхность атаки и лишний код.
 */
enum class Crud {
    /** `GET` - вся коллекция, с `?id=` - один документ. */
    READ,
    /** `GET /paged`. */
    PAGED,
    /** `GET /count`. */
    COUNT,
    /** `POST` - массив новых документов. */
    CREATE,
    /** `PUT ?id=` - изменённые поля. */
    UPDATE,
    /** `DELETE ?id=`. */
    DELETE,
}

/**
 * Маршруты коллекции: общий CRUD из [operations] и собственные маршруты наследника
 * в [additionalRoutes]. Путь выводится из имени сериализуемого класса. Справочнику без
 * своих маршрутов наследник не нужен - хватает экземпляра с нужными операциями.
 *
 * Справочник с [cache] читается из памяти, а не из базы: список целиком сериализуется один
 * раз на ревизию кеша, документ по id - O(1).
 */
@OptIn(ExperimentalKtorApi::class)
open class BaseRoute<T : StockEntity>(
    protected val repository: BaseRepository<T>,
    entitySerializer: KSerializer<T>,
    private val operations: Set<Crud>,
    private val cache: EntityCache<T>? = null,
) : RouteRegistrar {
    private val collection = entitySerializer.descriptor.serialName.substringAfterLast('.').lowercase()
    private val basePath = apiPath(collection)
    private val listSerializer = ListSerializer(entitySerializer)
    private val oneResponse = ApiMongoResponse.serializer(entitySerializer)
    private val listResponse = ApiMongoResponse.serializer(listSerializer)
    private val pagedResponse = ApiMongoResponse.serializer(PagedMongoResponse.serializer(entitySerializer))
    private val encodedList = AtomicReference<Pair<Long, String>?>(null)

    override fun register(routing: Routing) {
        routing.route(basePath) {
            additionalRoutes(this)
            if (Crud.PAGED in operations) pagedRoute()
            if (Crud.COUNT in operations) countRoute()
            if (Crud.READ in operations) readRoute()
            if (Crud.CREATE in operations) createRoute()
            if (Crud.UPDATE in operations) updateRoute()
            if (Crud.DELETE in operations) deleteRoute()
        }.describe {
            tag(collection)
        }.saveChildren()
    }

    protected open fun additionalRoutes(route: Route): Route = route

    private fun Route.readRoute() = get {
        val id = call.request.queryParameters["id"].orEmpty()
        when {
            id.isNotEmpty() -> {
                val key = requireId(id, "readRoute")
                call.respondJson(oneResponse, ApiMongoResponse.ok(cache?.findById(key) ?: repository.findById(key)))
            }
            cache != null -> call.respondText(cachedList(cache), ContentType.Application.Json)
            else -> call.respondJson(listResponse, ApiMongoResponse.ok(repository.findAll()))
        }
    }

    /** Ответ со всем справочником, собранный один раз на ревизию кеша. */
    private fun cachedList(cache: EntityCache<T>): String {
        val revision = cache.revision
        encodedList.get()?.takeIf { it.first == revision }?.let { return it.second }
        val text = AppJson.encodeToString(listResponse, ApiMongoResponse.ok(cache.getCache()))
        encodedList.set(revision to text)
        return text
    }

    private fun Route.pagedRoute() = get("/paged") {
        guarded("pagedRoute") {
            val paged = repository.findPaged(call.queryParam("page", 0), call.queryParam("size", CONST_PAGE_SIZE_DEFAULT))
            call.respondJson(pagedResponse, ApiMongoResponse.ok(paged))
        }
    }

    private fun Route.countRoute() = get("/count") {
        call.respondOk(mapOf("count" to repository.count()))
    }

    private fun Route.createRoute() = post {
        guarded("createRoute") {
            val entities = AppJson.decodeFromJsonElement(listSerializer, call.receive<JsonArray>())
            val created = transactionExecute("[$basePath::createRoute] $entities") { session ->
                repository.insertMany(entities, session)
            }
            call.respondJson(listResponse, ApiMongoResponse.ok(created))
        }
    }

    private fun Route.updateRoute() = put {
        guarded("updateRoute") {
            val id = call.idParam()
            // Служебные поля принадлежат базе: общий PUT их не пишет
            val updates = call.receive<JsonObject>()
                .filterKeys { it !in CONST_SYSTEM_FIELDS }
                .mapValues { (_, value) -> value.toNative() }
            val updated = transactionExecute("[$basePath::updateRoute] $id") { session ->
                repository.updateFields(id, updates, session)
            }
            call.respondJson(oneResponse, ApiMongoResponse.ok(updated))
        }
    }

    private fun Route.deleteRoute() = delete {
        guarded("deleteRoute") {
            val id = call.idParam()
            transactionExecute("[$basePath]::deleteRoute $id") { session -> repository.deleteById(id, session) }
            call.respondOk("system.deleted")
        }
    }

    /** Бизнес-ошибка уходит как есть, всё прочее - единой ошибкой маршрута с именем операции. */
    private inline fun guarded(method: String, block: () -> Unit) {
        try {
            block()
        } catch (e: BaseException) {
            throw e
        } catch (e: Exception) {
            throw BaseRouteExceptions.funException(method, e.message)
        }
    }
}

/** JSON-значение в тип, который драйвер Mongo запишет как есть. */
private fun JsonElement.toNative(): Any? = when (this) {
    is JsonNull -> null
    is JsonPrimitive -> when {
        isString -> content
        content == "true" || content == "false" -> content.toBoolean()
        '.' in content -> content.toDoubleOrNull()
        else -> content.toIntOrNull() ?: content.toLongOrNull() ?: content
    }
    is JsonArray -> map { it.toNative() }
    is JsonObject -> mapValues { it.value.toNative() }
}

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class ApiMongoResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: BaseException? = null,
    /** Снимок героя после команды (с 0.48.0) - только тому, кто попросил его заголовком. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val hero: features.logic.hero.HeroSnapshot? = null,
) {
    companion object {
        fun <T> ok(data: T?) = ApiMongoResponse(success = true, data = data)

        fun error(exception: BaseException) = ApiMongoResponse<Unit>(success = false, error = exception)
    }
}

@Serializable
data class PagedMongoResponse<T>(
    val items: List<T>,
    val page: Int,
    val totalItems: Long,
    val totalPages: Int
)

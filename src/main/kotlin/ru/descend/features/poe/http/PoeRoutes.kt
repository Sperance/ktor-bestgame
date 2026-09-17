package ru.descend.features.poe.http

import ru.descend.shared.http.receiveCommand
import ru.descend.infrastructure.security.actor
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import ru.descend.domain.enums.EnumUserRoles
import ru.descend.features.character.persistence.CharacterRepository
import ru.descend.features.equipment.persistence.EquipmentRepository
import ru.descend.domain.icons.IconResolver
import ru.descend.features.icons.IconBindings
import ru.descend.features.poe.application.PoeService
import ru.descend.features.poe.catalog.PoeRecord
import ru.descend.features.poe.catalog.string
import ru.descend.features.poe.domain.PoeCapabilities
import ru.descend.features.poe.domain.PoeCrafting
import ru.descend.features.poe.domain.PoeCurrency
import ru.descend.features.poe.domain.PoeInventory
import ru.descend.features.poe.persistence.MongoModifierCatalog
import ru.descend.features.user.persistence.UserRepository
import ru.descend.shared.error.BaseException
import ru.descend.shared.http.ApiMongoResponse

fun Route.poeRoutes() {
    val characters by inject<CharacterRepository>()
    val equipment by inject<EquipmentRepository>()
    val users by inject<UserRepository>()
    val catalogs by inject<MongoModifierCatalog>()
    val definitions by inject<ru.descend.features.modifiers.persistence.ModifierDefinitionRepository>()
    val service by inject<PoeService>()
    route("/api/v1/poe") {
        post("/token") {
            val request = call.receiveCommand<TokenRequest>()
            val user = users.authenticate(request.login, request.password)
            call.respond(ApiMongoResponse.ok(TokenResponse(GameJwt.issue(user._id, user.authVersion))))
        }
        get("/catalog") {
            val catalog = catalogs.snapshot().catalog
            val type = call.request.queryParameters["type"] ?: "bases"
            if (type !in setOf("bases", "modifiers")) { call.respond(HttpStatusCode.BadRequest); return@get }
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
            val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 50
            if (page !in 0..100000 || size !in 1..200) { call.respond(HttpStatusCode.BadRequest); return@get }
            val query = call.request.queryParameters["q"].orEmpty().take(200)
            val id = call.request.queryParameters["id"]
            val data = (if (type == "bases") catalog.bases else catalog.mods.filterKeys(catalog::enabled)).filter { (key, value) ->
                (id == null || key == id) && (query.isBlank() || key.contains(query, true) || value.string("name").contains(query, true) || value.string("text").contains(query, true))
            }
            val records = data.entries.drop(page * size).take(size).map { (key, value) ->
                // Каждая запись каталога приходит со своей иконкой: клиенту не нужен второй запрос.
                PoeRecord(key, value, if (type == "bases") IconBindings.forBase(value) else IconBindings.forRawModifier(key, value))
            }
            call.respond(ApiMongoResponse.ok(CatalogPage(records, page, size, data.size)))
        }
        get("/capabilities") { call.respond(ApiMongoResponse.ok(PoeCapabilities())) }
        get("/modifier-definitions") {
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
            val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 50
            if (page !in 0..100000 || size !in 1..100) { call.respond(HttpStatusCode.BadRequest); return@get }
            val query = call.request.queryParameters["q"].orEmpty().take(200)
            val snapshot = catalogs.snapshot()
            val latest = snapshot.definitions.values.groupBy { it.id }.values.map { revisions -> revisions.maxBy { it.revision } }
                .filter { (it.poe == null || snapshot.catalog.enabled(it.id)) && (query.isBlank() || it.id.contains(query, true) || it.name.contains(query, true)) }.sortedBy { it.id }
            val items = latest.drop(page * size).take(size)
            call.respond(ApiMongoResponse.ok(ModifierDefinitionPage(items, page, size, latest.size,
                icons = items.associate { it.id to IconResolver.forDefinition(it) })))
        }
        get("/modifier-definition") {
            val id = call.request.queryParameters["id"]
            if (id == null) { call.respond(HttpStatusCode.BadRequest); return@get }
            definitions.ensureRevisionIndex()
            val revision = call.request.queryParameters["revision"]?.toIntOrNull()
            val definition = if (revision == null) definitions.latest(id) else if (revision > 0) definitions.resolve(ru.descend.domain.modifiers.ModifierRef(id, revision)) else null
            if (definition == null) { call.respond(HttpStatusCode.NotFound); return@get }
            call.respond(ApiMongoResponse.ok(definition))
        }
        get("/currencies") {
            val catalog = catalogs.snapshot().catalog
            val inventory = PoeInventory(catalog, PoeCrafting(catalog))
            call.respond(ApiMongoResponse.ok(PoeCurrency.entries.map {
                CurrencyOption(it, it.displayName, inventory.currencyId(it), IconBindings.currencies.getValue(it))
            }))
        }
        authenticate("jwt-auth") {
            post("/modifier-definitions") {
                val actor = call.actor()
                val owner = actor.id
                if (!actor.admin) { call.respond(HttpStatusCode.Forbidden); return@post }
                try {
                    val request = call.receiveCommand<PublishModifierRequest>()
                    val definition = definitions.publish(request.definition, request.expectedRevision)
                    catalogs.invalidate()
                    call.respond(HttpStatusCode.Created, ApiMongoResponse.ok(definition))
                } catch (e: com.mongodb.MongoWriteException) {
                    if (e.error.code != 11000) throw e
                    call.respond(HttpStatusCode.Conflict, ApiMongoResponse.error(BaseException("Revision already published", "Modifiers", null, "MOD_CONFLICT")))
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ApiMongoResponse.error(BaseException("Invalid modifier definition", "Modifiers", null, "MOD_INVALID")))
                }
            }
            get("/characters/{characterId}/inventory") {
                val actor = call.actor()
                val owner = actor.id
                val character = characters.findById(requireNotNull(call.parameters["characterId"]))
                if (character == null || character.deleted || character.userId != owner) {
                    call.respond(HttpStatusCode.NotFound); return@get
                }
                call.respond(ApiMongoResponse.ok(InventoryResponse(character.version, character.equipments)))
            }
            post("/characters/{characterId}/craft") {
                val actor = call.actor()
                val owner = actor.id
                try {
                    call.respond(ApiMongoResponse.ok(service.craft(requireNotNull(call.parameters["characterId"]), owner, call.receiveCommand())))
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ApiMongoResponse.error(BaseException("Invalid craft request", "PoeCraft", null, "POE_INVALID")))
                }
            }
            post("/characters/{characterId}/drop") {
                val actor = call.actor()
                val owner = actor.id
                if (!actor.admin) {
                    call.respond(HttpStatusCode.Forbidden); return@post
                }
                try {
                    call.respond(ApiMongoResponse.ok(service.drop(requireNotNull(call.parameters["characterId"]), owner, call.receiveCommand())))
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ApiMongoResponse.error(BaseException("Invalid drop request", "PoeDrop", null, "POE_INVALID")))
                }
            }
        }
    }
}

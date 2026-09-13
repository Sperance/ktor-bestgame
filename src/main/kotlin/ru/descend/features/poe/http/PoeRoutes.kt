package ru.descend.features.poe.http

import ru.descend.features.poe.application.PoeService

import ru.descend.features.poe.catalog.PoeCatalog
import ru.descend.features.poe.catalog.PoeRecord

import ru.descend.features.poe.catalog.string
import ru.descend.features.poe.domain.PoeCapabilities
import ru.descend.features.poe.domain.PoeCrafting
import ru.descend.features.poe.domain.PoeCurrency
import ru.descend.features.poe.domain.PoeInventory

import ru.descend.features.poe.persistence.MongoModifierCatalog

import ru.descend.domain.enums.EnumUserRoles
import ru.descend.shared.error.BaseException
import ru.descend.shared.http.ApiMongoResponse
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import ru.descend.features.character.persistence.CharacterRepository
import ru.descend.features.equipment.persistence.EquipmentRepository
import ru.descend.features.user.persistence.UserRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.security.SecureRandom
import java.util.Base64
import java.util.Date

fun Route.poeRoutes() {
    val characters by inject<CharacterRepository>()
    val equipment by inject<EquipmentRepository>()
    val users by inject<UserRepository>()
    val catalogs by inject<MongoModifierCatalog>()
    val definitions by inject<ru.descend.features.modifiers.persistence.ModifierDefinitionRepository>()
    val service by inject<PoeService>()
    route("/api/v1/poe") {
        post("/token") {
            val request = call.receive<TokenRequest>()
            val user = users.authenticate(request.login, request.password)
            call.respond(ApiMongoResponse.ok(TokenResponse(GameJwt.issue(user._id))))
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
            call.respond(ApiMongoResponse.ok(CatalogPage(data.entries.drop(page * size).take(size).map { PoeRecord(it.key, it.value) }, page, size, data.size)))
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
            call.respond(ApiMongoResponse.ok(ModifierDefinitionPage(latest.drop(page * size).take(size), page, size, latest.size)))
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
            call.respond(ApiMongoResponse.ok(PoeCurrency.entries.map { CurrencyOption(it, it.displayName, inventory.currencyId(it)) }))
        }
        authenticate("jwt-auth") {
            post("/modifier-definitions") {
                val owner = requireNotNull(call.principal<JWTPrincipal>()?.payload?.subject)
                if (users.findById(owner)?.role != EnumUserRoles.ADMIN) { call.respond(HttpStatusCode.Forbidden); return@post }
                try {
                    val request = call.receive<PublishModifierRequest>()
                    val definition = definitions.publish(request.definition, request.expectedRevision)
                    catalogs.invalidate()
                    call.respond(HttpStatusCode.Created, ApiMongoResponse.ok(definition))
                } catch (e: com.mongodb.MongoWriteException) {
                    if (e.error.code != 11000) throw e
                    call.respond(HttpStatusCode.Conflict, ApiMongoResponse.error(BaseException("Revision already published", "Modifiers", null, "MOD_CONFLICT")))
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ApiMongoResponse.error(BaseException(e.message, "Modifiers", null, "MOD_INVALID")))
                }
            }
            get("/characters/{characterId}/inventory") {
                val owner = requireNotNull(call.principal<JWTPrincipal>()?.payload?.subject)
                val character = characters.findById(requireNotNull(call.parameters["characterId"]))
                if (character == null || character.deleted || character.userId != owner) {
                    call.respond(HttpStatusCode.NotFound); return@get
                }
                call.respond(ApiMongoResponse.ok(InventoryResponse(character.version, character.equipments)))
            }
            post("/characters/{characterId}/craft") {
                val owner = requireNotNull(call.principal<JWTPrincipal>()?.payload?.subject)
                try {
                    call.respond(ApiMongoResponse.ok(service.craft(requireNotNull(call.parameters["characterId"]), owner, call.receive())))
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ApiMongoResponse.error(BaseException(e.message, "PoeCraft", null, "POE_INVALID")))
                }
            }
            post("/characters/{characterId}/drop") {
                val owner = requireNotNull(call.principal<JWTPrincipal>()?.payload?.subject)
                if (users.findById(owner)?.role != EnumUserRoles.ADMIN) {
                    call.respond(HttpStatusCode.Forbidden); return@post
                }
                try {
                    call.respond(ApiMongoResponse.ok(service.drop(requireNotNull(call.parameters["characterId"]), owner, call.receive())))
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ApiMongoResponse.error(BaseException(e.message, "PoeDrop", null, "POE_INVALID")))
                }
            }
        }
    }
}

package features.poe

import application.enums.EnumUserRoles
import base.exception.BaseException
import base.route.ApiMongoResponse
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import features.data.character.CharacterRepository
import features.data.equipment.EquipmentRepository
import features.data.user.UserRepository
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

object GameJwt {
    val secret: String = System.getenv("JWT_SECRET")?.also { require(it.length >= 32) { "JWT_SECRET must have at least 32 characters" } }
        ?: Base64.getEncoder().encodeToString(ByteArray(48).also { SecureRandom().nextBytes(it) })
    fun issue(userId: String): String = JWT.create().withIssuer("ktor-server").withAudience("ktor-client")
        .withSubject(userId).withIssuedAt(Date()).withExpiresAt(Date(System.currentTimeMillis() + 3600000))
        .sign(Algorithm.HMAC256(secret))
}
@Serializable data class TokenRequest(val login: String, val password: String)
@Serializable data class TokenResponse(val token: String, val expiresIn: Int = 3600)
@Serializable data class CatalogPage(val items: List<PoeRecord>, val page: Int, val size: Int, val total: Int)
@Serializable data class CurrencyOption(val id: PoeCurrency, val name: String, val itemId: String)

fun Route.poeRoutes() {
    val characters by inject<CharacterRepository>()
    val equipment by inject<EquipmentRepository>()
    val users by inject<UserRepository>()
    val service by lazy { PoeService(characters, equipment) }
    route("/api/v1/poe") {
        post("/token") {
            val request = call.receive<TokenRequest>()
            val user = users.authenticate(request.login, request.password)
            call.respond(ApiMongoResponse.ok(TokenResponse(GameJwt.issue(user._id))))
        }
        get("/catalog") {
            val catalog = PoeCatalog.bundled
            val type = call.request.queryParameters["type"] ?: "bases"
            if (type !in setOf("bases", "modifiers")) { call.respond(HttpStatusCode.BadRequest); return@get }
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
            val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 50
            if (page !in 0..100000 || size !in 1..200) { call.respond(HttpStatusCode.BadRequest); return@get }
            val query = call.request.queryParameters["q"].orEmpty().take(200)
            val id = call.request.queryParameters["id"]
            val data = (if (type == "bases") catalog.bases else catalog.mods).filter { (key, value) ->
                (id == null || key == id) && (query.isBlank() || key.contains(query, true) || value.string("name").contains(query, true) || value.string("text").contains(query, true))
            }
            call.respond(ApiMongoResponse.ok(CatalogPage(data.entries.drop(page * size).take(size).map { PoeRecord(it.key, it.value) }, page, size, data.size)))
        }
        get("/currencies") {
            val inventory = PoeInventory(PoeCatalog.bundled, PoeCrafting(PoeCatalog.bundled))
            call.respond(ApiMongoResponse.ok(PoeCurrency.entries.map { CurrencyOption(it, it.displayName, inventory.currencyId(it)) }))
        }
        authenticate("jwt-auth") {
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

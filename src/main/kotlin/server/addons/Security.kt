package server.addons

import application.enums.EnumUserRoles
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import extensions.saveChildren
import io.ktor.server.application.*
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.hide
import io.ktor.server.sessions.*
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalKtorApi::class)
fun Application.configureSecurity() {

    val secret = "your-secret-key-12345"
    val secretEncryptKey = "00112233445566778899aabbccddeeff".hexToByteArray()
    val secretSignKey = "6819b57a326945c1968f45236589".hexToByteArray()

    install(Authentication) {
        jwt("jwt-auth") {
            verifier(
                JWT
                    .require(Algorithm.HMAC256(secret))
                    .withIssuer("ktor-server")
                    .build()
            )
            validate { credential ->
                if (credential.payload.audience.contains("ktor-client")) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }

    install(Sessions) {
        cookie<UserSession>("auth_session") {
            cookie.path = "/"
            cookie.httpOnly = true
            cookie.secure = true
            cookie.maxAge = 24.hours // 24 часа

            // Шифрование через JWT
            transform(
                SessionTransportTransformerEncrypt(
                    secretEncryptKey,
                    secretSignKey
                )
            )
        }
    }

    routing {
        get("/login") {
            // Создаем сессию при логине
            call.sessions.set(UserSession(userId = UUID.randomUUID().toString(), username = "alex", role = EnumUserRoles.USER))
            call.respondText("Вы вошли в систему!")
        }.hide()

        get("/profile") {
            // Получаем данные из сессии
            val session = call.sessions.get<UserSession>()
            if (session != null) {
                call.respondText("Привет, $session!")
            } else {
                call.respondText("Вы не авторизованы")
            }
        }.hide()

        get("/logout") {
            // Завершаем сессию
            call.sessions.clear<UserSession>()
            call.respondText("Вы вышли из системы")
        }.hide()

        authenticate("jwt-auth") {
            get("/session/profile") {
                // Получаем данные из сессии
                val session = call.sessions.get<UserSession>()
                if (session != null) {
                    call.respondText("Привет, $session!")
                } else {
                    call.respondText("Вы не авторизованы")
                }
            }.hide()
        }
    }.saveChildren()
}

@Serializable
data class UserSession(
    val userId: String,
    val username: String,
    val role: EnumUserRoles,
    val createdAt: Long = System.currentTimeMillis()
) {
    override fun toString(): String {
        return "UserSession(userId='$userId', username='$username', role=$role, createdAt=$createdAt)"
    }
}

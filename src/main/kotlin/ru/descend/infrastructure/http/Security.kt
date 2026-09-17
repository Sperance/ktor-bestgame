package ru.descend.infrastructure.http

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
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
import java.util.UUID
import kotlin.time.Duration.Companion.hours
import kotlinx.serialization.Serializable
import ru.descend.domain.enums.EnumUserRoles
import ru.descend.shared.extensions.saveChildren

@OptIn(ExperimentalKtorApi::class)
fun Application.configureSecurity() {

    val secret = ru.descend.features.poe.http.GameJwt.secret
    val secretEncryptKey = java.security.MessageDigest.getInstance("SHA-256").digest((secret + ":cookie-encryption").toByteArray())
    val secretSignKey = java.security.MessageDigest.getInstance("SHA-256").digest((secret + ":cookie-signing").toByteArray())

    install(Authentication) {
        jwt("jwt-auth") {
            verifier(
                JWT
                    .require(Algorithm.HMAC256(secret))
                    .withIssuer("ktor-server")
                    .build()
            )
            validate { credential ->
                if (credential.payload.audience.contains("ktor-client") && !credential.payload.subject.isNullOrBlank() &&
                    org.koin.core.context.GlobalContext.get().get<ru.descend.features.user.persistence.UserRepository>().findById(credential.payload.subject)?.let {
                        !it.deleted && it.isActive && credential.payload.getClaim("authVersion").asLong() == it.authVersion
                    } == true) {
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

}

@Serializable
@kotlinx.serialization.SerialName("server.addons.UserSession")
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

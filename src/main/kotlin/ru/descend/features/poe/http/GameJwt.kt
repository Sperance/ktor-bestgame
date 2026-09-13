package ru.descend.features.poe.http

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.routing.*
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

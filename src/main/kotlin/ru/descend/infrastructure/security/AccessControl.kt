package ru.descend.infrastructure.security

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import org.koin.ktor.ext.getKoin
import ru.descend.domain.enums.EnumUserRoles
import ru.descend.features.user.persistence.UserRepository
import ru.descend.features.character.model.Character
import ru.descend.shared.http.*

data class Actor(val id: String, val admin: Boolean) {
    fun requireAdmin() { if (!admin) forbidden() }
    fun own(character: Character) { if (character.deleted || (!admin && character.userId != id)) missing() }
}
suspend fun ApplicationCall.actor(): Actor {
    val principal = principal<JWTPrincipal>() ?: throw ApiFailure(HttpStatusCode.Unauthorized, "UNAUTHORIZED", "Authentication required")
    val user = application.getKoin().get<UserRepository>().findById(principal.payload.subject) ?: missing()
    if (user.deleted || !user.isActive || principal.payload.getClaim("authVersion").asLong() != user.authVersion)
        throw ApiFailure(HttpStatusCode.Unauthorized, "UNAUTHORIZED", "Session expired")
    return Actor(user._id, user.role == EnumUserRoles.ADMIN)
}

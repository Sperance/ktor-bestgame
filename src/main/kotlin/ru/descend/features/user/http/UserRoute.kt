package ru.descend.features.user.http

import io.ktor.server.response.respond
import io.ktor.server.routing.*
import ru.descend.features.user.model.*
import ru.descend.features.user.persistence.UserRepository
import ru.descend.infrastructure.mongo.MongoFactory.transactionExecute
import ru.descend.infrastructure.security.*
import ru.descend.shared.http.*
import ru.descend.shared.http.commands.*

class UserRoute(val repo: UserRepository) : BaseRoute<User, UserResponse>(repo, User.serializer(), UserResponse.serializer(), { it.toResponse() }) {
    override fun additionalRoutes(route: Route) = with(route) {
        post("/changePassword") {
            val actor = call.actor(); val command = call.receiveCommand<ChangePasswordCommand>()
            PasswordHasher.validate(command.newPassword)
            val result = transactionExecute("user.changePassword") { session ->
                val user = repo.findById(actor.id, session) ?: missing()
                checkVersion(user.version, command.expectedVersion)
                if (!PasswordHasher.verify(command.currentPassword, user.password, user.salt)) forbidden()
                user.password = PasswordHasher.hash(command.newPassword); user.salt = ""; user.authVersion++
                repo.update(user, session)
                user.toResponse()
            }
            call.respond(ApiMongoResponse.ok(result))
        }
        post("/changeRole") {
            val actor = call.actor(); actor.requireAdmin()
            val id = checkedId(call.request.queryParameters["id"])
            if (id == actor.id) invalid("Administrators cannot change their own role")
            val command = call.receiveCommand<ChangeRoleCommand>()
            val result = transactionExecute("user.changeRole") { session ->
                val user = repo.findById(id, session)?.takeUnless { it.deleted } ?: missing()
                checkVersion(user.version, command.expectedVersion)
                user.role = command.role; user.authVersion++
                repo.update(user, session); user.toResponse()
            }
            call.respond(ApiMongoResponse.ok(result))
        }
        // Passwords in GET parameters and device-ID authentication deliberately have no route.
    }
}

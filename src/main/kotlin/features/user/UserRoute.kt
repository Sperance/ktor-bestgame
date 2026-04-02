package features.user

import base.exception.NotFoundException
import base.model.ApiResponse
import base.route.BaseRoute
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer

class UserRoute(
    private val userService: UserService = UserService()
) : BaseRoute<User, UsersTable>(
    service = userService,
    basePath = "/api/users",
    entitySerializer = User.serializer()   // ← явный сериализатор
) {

    override fun additionalRoutes(route: Route) = with(route) {

        get("/search") {
            val name = call.queryParam("name")
            val users = userService.searchByName(name)
            call.respondEntityList(users)
        }

        get("/active") {
            val users = userService.findActive()
            call.respondEntityList(users)
        }

        get("/by-email/{email}") {
            val email = call.parameters["email"]!!
            val user = userService.findByEmail(email)
                ?: throw NotFoundException("User with email '$email' not found")
            call.respondEntity(user)
        }
    }
}
package features.user

import base.exception.NotFoundException
import base.route.BaseRoute
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

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
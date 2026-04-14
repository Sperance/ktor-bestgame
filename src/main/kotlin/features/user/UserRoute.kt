package features.user

import base.exception.BadRequestException
import base.exception.NotFoundException
import base.route.BaseRoute
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class UserRoute(
    private val userService: UserService = UserService()
) : BaseRoute<User, UsersTable>(
    service = userService,
    basePath = "/api/users",
    entitySerializer = User.serializer()
) {

    override fun additionalRoutes(route: Route) = with(route) {

        get("/login") {
            val login = call.queryParam("login")
            val password = call.queryParam("password")
            val user = userService.authenticate(login, password)
            call.respondEntity(user)
        }

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
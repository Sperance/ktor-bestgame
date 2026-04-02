package features.user

import base.exception.NotFoundException
import base.model.ApiResponse
import base.route.BaseRoute
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer

class UserRoute(
    private val userService: UserService = UserService()
) : BaseRoute<UserResponse, CreateUserRequest, UpdateUserRequest>(userService, "/api/users") {

    override val entitySerializer: KSerializer<UserResponse> = UserResponse.serializer()

    override suspend fun deserializeCreate(call: ApplicationCall) =
        call.receive<CreateUserRequest>()

    override suspend fun deserializeUpdate(call: ApplicationCall) =
        call.receive<UpdateUserRequest>()

    override fun additionalRoutes(route: Route) = with(route) {

        get("/search") {
            val name = call.queryParam("name")
            call.respondJson(
                HttpStatusCode.OK,
                ApiResponse.serializer(ListSerializer(entitySerializer)),
                ApiResponse.ok(userService.searchByName(name))
            )
        }

        get("/active") {
            call.respondJson(
                HttpStatusCode.OK,
                ApiResponse.serializer(ListSerializer(entitySerializer)),
                ApiResponse.ok(userService.findActive())
            )
        }

        get("/by-email/{email}") {
            val email = call.parameters["email"]!!
            val user = userService.findByEmail(email)
                ?: throw NotFoundException("User with email '$email' not found")
            call.respondJson(
                HttpStatusCode.OK,
                ApiResponse.serializer(entitySerializer),
                ApiResponse.ok(user)
            )
        }
    }
}

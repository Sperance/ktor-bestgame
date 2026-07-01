package features.user

import base.route.ApiMongoResponse
import base.route.BaseRoute
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

class UserRoute : BaseRoute<User, UserResponse>(
    repository = UserRepositoryMongo,
    entitySerializer = User.serializer(),
    responseSerializer = UserResponse.serializer(),
    toResponse = { it.toResponse() }
) {

    override fun additionalRoutes(route: Route) = with(route) {

        get("/login") {
            val login = call.queryParam("login")
            val password = call.queryParam("password")
            val user = UserRepositoryMongo.authenticate(login, password).toResponse()
            call.respond(ApiMongoResponse.ok(user))
        }

        route("/search") {
            get("/active") {
                val users = UserRepositoryMongo.findActive().map { it.toResponse() }
                call.respond(ApiMongoResponse.ok(users))
            }
            get("/name") {
                val name = call.queryParam("name")
                val users = UserRepositoryMongo.searchByName(name).map { it.toResponse() }
                call.respond(ApiMongoResponse.ok(users))
            }
            get("/email") {
                val email = call.queryParam("email")
                val user = UserRepositoryMongo.findByEmail(email)?.toResponse()
                call.respond(ApiMongoResponse.ok(user))
            }
        }

        get("/changePassword") {
            val id = call.idParam()
            val password = call.queryParam("password")
            val newPassword = call.queryParam("new_password")
            call.respond(ApiMongoResponse.ok(UserRepositoryMongo.changePassword(id, password, newPassword)))
        }
    }
}
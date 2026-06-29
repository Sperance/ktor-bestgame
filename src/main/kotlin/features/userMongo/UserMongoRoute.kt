package features.userMongo

import base.model.ApiResponse
import base.route.BaseRouteMongo
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

class UserMongoRoute : BaseRouteMongo<UserMongo, UserMongoResponse>(
    repository = UserRepositoryMongo,
    basePath = "/api/users_mongo",
    entitySerializer = UserMongo.serializer(),
    responseSerializer = UserMongoResponse.serializer(),
    toResponse = { it.toResponse() }
) {

    override fun additionalRoutes(route: Route) = with(route) {

        get("/login") {
            val login = call.queryParam("login")
            val password = call.queryParam("password")
            val user = UserRepositoryMongo.authenticate(login, password).toResponse()
            call.respond(ApiResponse.ok(user))
        }

        route("/search") {
            get("/active") {
                val users = UserRepositoryMongo.findActive().map { it.toResponse() }
                call.respond(ApiResponse.ok(users))
            }
            get("/name") {
                val name = call.queryParam("name")
                val users = UserRepositoryMongo.searchByName(name).map { it.toResponse() }
                call.respond(ApiResponse.ok(users))
            }
            get("/email") {
                val email = call.queryParam("email")
                val user = UserRepositoryMongo.findByEmail(email)?.toResponse()
                call.respond(ApiResponse.ok(user))
            }
        }

        get("/changePassword") {
            val id = call.idParam()
            val password = call.queryParam("password")
            val newPassword = call.queryParam("new_password")
            call.respond(ApiResponse.ok(UserRepositoryMongo.changePassword(id, password, newPassword)))
        }
    }
}
package features.userMongo

import base.exception.NotFoundException
import base.model.ApiResponse
import base.route.BaseRouteMongo
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

class UserMongoRoute(
    private val userMongoRepository: UserRepositoryMongo = UserRepositoryMongo()
) : BaseRouteMongo<UserMongo, UserMongoResponse>(
    repository = userMongoRepository,
    basePath = "/api/users_mongo",
    entitySerializer = UserMongo.serializer(),
    responseSerializer = UserMongoResponse.serializer(),
    toResponse = { it.toResponse() }
) {

    override fun additionalRoutes(route: Route) = with(route) {

        get("/login") {
            val login = call.queryParam("login")
            val password = call.queryParam("password")
            val user = userMongoRepository.authenticate(login, password)
            call.respond(ApiResponse.ok(user))
        }

        get("/search") {
            val name = call.queryParam("name")
            val users = userMongoRepository.searchByName(name)
            call.respond(ApiResponse.ok(users))
        }

        get("/active") {
            val users = userMongoRepository.findActive()
            call.respond(ApiResponse.ok(users))
        }

        get("/by-email/{email}") {
            val email = call.parameters["email"]!!
            val user = userMongoRepository.findByEmail(email) ?: throw NotFoundException("User with email '$email' not found")
            call.respond(ApiResponse.ok(user))
        }

        get("/changePassword") {
            val id = call.idParam()
            val password = call.queryParam("password")
            val newPassword = call.queryParam("new_password")
            call.respond(ApiResponse.ok(userMongoRepository.changePassword(id, password, newPassword)))
        }
    }
}
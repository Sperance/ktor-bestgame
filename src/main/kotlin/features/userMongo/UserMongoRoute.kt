package features.userMongo

import base.exception.NotFoundException
import features.BaseRouteMongo
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

class UserMongoRoute(
    private val userMongoService: UserMongoService = UserMongoService()
) : BaseRouteMongo<UserMongo>(
    service = userMongoService,
    basePath = "/api/users_mongo",
    entitySerializer = UserMongo.serializer()
) {

    override fun additionalRoutes(route: Route) = with(route) {

        get("/login") {
            val login = call.queryParam("login")
            val password = call.queryParam("password")
            val user = userMongoService.authenticate(login, password)
            call.respondEntity(user)
        }

        get("/search") {
            val name = call.queryParam("name")
            val users = userMongoService.searchByName(name)
            call.respondEntityList(users)
        }

        get("/active") {
            val users = userMongoService.findActive()
            call.respondEntityList(users)
        }

        get("/by-email/{email}") {
            val email = call.parameters["email"]!!
            val user = userMongoService.findByEmail(email) ?: throw NotFoundException("User with email '$email' not found")
            call.respondEntity(user)
        }
    }
}
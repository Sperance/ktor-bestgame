package features.data.property

import base.route.ApiMongoResponse
import base.route.BaseRoute
import features.caches.PropertyCache
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

class PropertyRoute(repo: PropertyRepository) : BaseRoute<Property, Property>(
    repository = repo,
    entitySerializer = Property.serializer(),
    responseSerializer = Property.serializer(),
    toResponse = { it }
) {
    override fun additionalRoutes(route: Route) = with(route) {
        get("/cache/hash") {
            val data = PropertyCache.getCacheHash()
            call.respond(ApiMongoResponse.ok(data))
        }
    }
}
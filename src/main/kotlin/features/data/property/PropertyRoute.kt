package features.data.property

import base.route.ApiMongoResponse
import base.route.BaseRoute
import features.caches.PropertyCache
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class PropertyRoute(repo: PropertyRepository) : BaseRoute<Property, Property>(
    repository = repo,
    entitySerializer = Property.serializer(),
    responseSerializer = Property.serializer(),
    toResponse = { it }
), KoinComponent {
    private val propertyCache: PropertyCache by inject()

    override fun additionalRoutes(route: Route) = with(route) {
        get("/cache/hash") {
            val data = propertyCache.getCacheHash()
            call.respond(ApiMongoResponse.ok(data))
        }
    }
}
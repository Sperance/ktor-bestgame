package features.property

import base.route.BaseRoute
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

class PropertyRoute(
    val statsService: PropertyService = PropertyService()
) : BaseRoute<Property, PropertyTable>(
    service = statsService,
    basePath = "/api/property",
    entitySerializer = Property.serializer()
) {
    override fun additionalRoutes(route: Route) = with(route) {
        get("/cache") {
            call.respondEntityList(PropertyCache.getAll())
        }
        get("/cache/refresh") {
            PropertyCache.refresh(statsService.statsRepo)
            call.respondEntityList(PropertyCache.getAll())
        }
    }
}
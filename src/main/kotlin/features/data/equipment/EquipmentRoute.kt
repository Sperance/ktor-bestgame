package features.data.equipment

import base.route.ApiMongoResponse
import base.route.BaseRoute
import features.caches.EquipmentCache
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

class EquipmentRoute(repo: EquipmentRepository) : BaseRoute<Equipment, Equipment>(
    repository = repo,
    entitySerializer = Equipment.serializer(),
    responseSerializer = Equipment.serializer(),
    toResponse = { it }
) {
    override fun additionalRoutes(route: Route) = with(route) {
        get("/cache/hash") {
            val data = EquipmentCache.getCacheHash()
            call.respond(ApiMongoResponse.ok(data))
        }
    }
}
package features.data.equipmentName

import base.route.ApiMongoResponse
import base.route.BaseRoute
import features.caches.EquipmentNameCache
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class EquipmentNameRoute(repo: EquipmentNameRepository) : BaseRoute<EquipmentName, EquipmentName>(
    repository = repo,
    entitySerializer = EquipmentName.serializer(),
    responseSerializer = EquipmentName.serializer(),
    toResponse = { it }
), KoinComponent {
    private val equipmentNameCache: EquipmentNameCache by inject()

    override fun additionalRoutes(route: Route) = with(route) {
        get("/cache/hash") {
            val data = equipmentNameCache.getCacheHash()
            call.respond(ApiMongoResponse.ok(data))
        }
    }
}
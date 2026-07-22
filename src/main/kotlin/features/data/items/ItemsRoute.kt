package features.data.items

import base.route.ApiMongoResponse
import base.route.BaseRoute
import features.caches.ItemsCache
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

class ItemsRoute(repo: ItemsRepository) : BaseRoute<Items, Items>(
    repository = repo,
    entitySerializer = Items.serializer(),
    responseSerializer = Items.serializer(),
    toResponse = { it }
) {
    override fun additionalRoutes(route: Route) = with(route) {
        get("/cache/hash") {
            val data = ItemsCache.getCacheHash()
            call.respond(ApiMongoResponse.ok(data))
        }
    }
}
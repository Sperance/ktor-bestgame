package features.data.blockList

import base.route.ApiMongoResponse
import base.route.BaseRoute
import features.caches.BlockListCache
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

class BlockListRoute(repo: BlockListRepository) : BaseRoute<BlockList, BlockList>(
    repository = repo,
    entitySerializer = BlockList.serializer(),
    responseSerializer = BlockList.serializer(),
    toResponse = { it }
) {
    override fun additionalRoutes(route: Route) = with(route) {
        get("/cache/hash") {
            val data = BlockListCache.getCacheHash()
            call.respond(ApiMongoResponse.ok(data))
        }
    }
}
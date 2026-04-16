package features.items

import base.route.BaseRoute
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

class ItemsRoute(
    val itemService: ItemsService = ItemsService()
) : BaseRoute<Item, ItemsTable>(
    service = itemService,
    basePath = "/api/items",
    entitySerializer = Item.serializer()
) {
    override fun additionalRoutes(route: Route) = with(route) {
        get("/cache") {
            call.respondEntityList(ItemsCache.getAll())
        }
        get("/cache/refresh") {
            ItemsCache.refresh(itemService.itemRepo)
            call.respondEntityList(ItemsCache.getAll())
        }
    }
}
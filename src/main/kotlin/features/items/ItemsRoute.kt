package features.items

import base.route.BaseRoute

class ItemsRoute(
    itemService: ItemsService = ItemsService()
) : BaseRoute<Item, ItemsTable>(
    service = itemService,
    basePath = "/api/items",
    entitySerializer = Item.serializer()
)
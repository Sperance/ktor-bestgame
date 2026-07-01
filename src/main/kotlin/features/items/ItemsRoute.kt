package features.items

import base.route.BaseRoute

class ItemsRoute : BaseRoute<Items, Items>(
    repository = ItemsRepository,
    entitySerializer = Items.serializer(),
    responseSerializer = Items.serializer(),
    toResponse = { it }
)
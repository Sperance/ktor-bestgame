package features.items

import base.route.BaseRoute

class ItemsRoute(
    repo: ItemsRepository
) : BaseRoute<Items, Items>(
    repository = repo,
    entitySerializer = Items.serializer(),
    responseSerializer = Items.serializer(),
    toResponse = { it }
)
package features.data.property

import base.route.BaseRoute

class PropertyRoute(
    repo: PropertyRepository
) : BaseRoute<Property, Property>(
    repository = repo,
    entitySerializer = Property.serializer(),
    responseSerializer = Property.serializer(),
    toResponse = { it }
)
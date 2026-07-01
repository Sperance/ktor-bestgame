package features.property

import base.route.BaseRoute

class PropertyRoute : BaseRoute<Property, Property>(
    repository = PropertyRepository,
    entitySerializer = Property.serializer(),
    responseSerializer = Property.serializer(),
    toResponse = { it }
)
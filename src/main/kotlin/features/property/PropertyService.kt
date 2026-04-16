package features.property

import base.service.BaseService

class PropertyService(
    val statsRepo: PropertyRepository = PropertyRepository()
) : BaseService<Property, PropertyTable>(statsRepo, Property.serializer())
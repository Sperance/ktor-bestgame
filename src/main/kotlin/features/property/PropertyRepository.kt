package features.property

import base.repository.BaseRepository

class PropertyRepository : BaseRepository<Property, PropertyTable>(
    table = PropertyTable,
    entityClass = Property::class
) {
    override val entityName = "Property"

    init {
        PropertyCache.refresh(this)
    }
}

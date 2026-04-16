package features.items

import base.repository.BaseRepository

class ItemsRepository : BaseRepository<Item, ItemsTable>(
    table = ItemsTable,
    entityClass = Item::class
) {
    override val entityName = "Items"

    init {
        ItemsCache.refresh(this)
    }
}

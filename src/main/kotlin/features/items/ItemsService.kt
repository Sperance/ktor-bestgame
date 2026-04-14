package features.items

import base.service.BaseService

class ItemsService(
    val itemRepo: ItemsRepository = ItemsRepository()
) : BaseService<Item, ItemsTable>(itemRepo, Item.serializer()) {

    override fun entityName() = "Items"
}

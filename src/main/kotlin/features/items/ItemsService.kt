package features.items

import base.exception.ConflictException
import base.service.BaseService

class ItemsService(
    val itemRepo: ItemsRepository = ItemsRepository()
) : BaseService<Item, ItemsTable>(itemRepo, Item.serializer()) {

    override fun entityName() = "Items"

    override fun validateCreate(entity: Item) {
        itemRepo.findByName(entity.name)?.let {
            throw ConflictException("Item with name '${entity.name}' is already exists (id ${it.id})")
        }
    }
}

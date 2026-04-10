package features.items

import base.repository.BaseRepository
import application.enums.EnumEquipmentType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class ItemsRepository : BaseRepository<Item, ItemsTable>(
    table = ItemsTable,
    entityClass = Item::class
) {
    override val entityName = "Items"
}

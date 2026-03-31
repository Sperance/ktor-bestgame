package application.data.inventory

import application.data.ExposedBaseDao
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class DAOinventory : ExposedBaseDao<InventoryTable, InventoryEntity, SnapshotInventory>(
    InventoryTable,
    InventoryEntity.Companion
) {
    override fun mapDtoToEntity(dto: SnapshotInventory, entity: InventoryEntity) {
        entity.items = dto._items
    }

    @Deprecated(level = DeprecationLevel.HIDDEN, message = "Объект инвентаря создаётся автоматически при создании персонажа (Character). Создавать его отдельно не нужно. Будет ошибка Уникальности")
    override fun create(body: InventoryEntity.() -> Unit): InventoryEntity {
        val entity = InventoryEntity.Companion.new {
            body()
        }
        return entity
    }
}
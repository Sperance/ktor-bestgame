package application.data.inventory

import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import application.data.ExposedBaseDao
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class DAOinventory : ExposedBaseDao<InventoryTable, InventoryEntity>(
    InventoryTable,
    InventoryEntity.Companion
) {
    override fun applyEntityToStatement(entity: InventoryEntity, stmt: UpdateStatement) {
        stmt[table.character] = entity.character.id
    }

    @Deprecated(level = DeprecationLevel.HIDDEN, message = "Объект инвентаря создаётся автоматически при создании персонажа (Character). Создавать его отдельно не нужно. Будет ошибка Уникальности")
    override fun create(body: InventoryEntity.() -> Unit): InventoryEntity {
        val entity = InventoryEntity.Companion.new {
            body()
        }
        return entity
    }
}
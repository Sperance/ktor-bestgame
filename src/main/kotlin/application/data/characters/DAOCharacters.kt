package application.data.characters

import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import application.data.ExposedBaseDao
import application.data.inventory.InventoryEntity
import extensions.printLog

class DAOCharacters : ExposedBaseDao<CharactersTable, CharacterEntity>(
    CharactersTable,
    CharacterEntity.Companion
) {
    override fun applyEntityToStatement(entity: CharacterEntity, stmt: UpdateStatement) {
        stmt[table.user] = entity.user.id
        stmt[table.name] = entity.name
        stmt[table.level] = entity.level
        stmt[table.experience] = entity.experience
        stmt[table.params] = entity.params
        stmt[table.buffs] = entity.buffs
        stmt[table.bools] = entity.bools
    }

    @Suppress("SENSELESS_COMPARISON")
    override fun create(body: CharacterEntity.() -> Unit): CharacterEntity {
        val entity = CharacterEntity.new char@ {
            body()
        }

        if (entity.params == null) {
            entity.params = entity.getStockParams()
        }

        if (entity.inventory.empty()) {
            InventoryEntity.new inv@{
                this.character = entity
                this.items = mutableSetOf()
            }
            printLog("Inventory created: ${entity.name}")
        }

        return entity
    }
}
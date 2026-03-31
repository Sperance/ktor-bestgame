package application.data.characters

import application.data.ExposedBaseDao
import application.data.inventory.InventoryEntity
import extensions.printLog

class DAOCharacters : ExposedBaseDao<CharactersTable, CharacterEntity, SnapshotCharacter>(
    CharactersTable,
    CharacterEntity.Companion
) {
    override fun mapDtoToEntity(dto: SnapshotCharacter, entity: CharacterEntity) {
        entity.name = dto._name
        entity.level = dto._level
        entity.experience = dto._experience
        entity.params = dto._params
        entity.buffs = dto._buffs
        entity.bools = dto._bools
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
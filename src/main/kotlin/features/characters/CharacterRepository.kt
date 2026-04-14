package features.characters

import application.model.ItemStock
import base.exception.BadRequestException
import base.exception.NotFoundException
import base.exception.OptimisticLockException
import base.repository.BaseRepository
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.core.eq

class CharacterRepository : BaseRepository<Character, CharacterTable>(
    table = CharacterTable,
    entityClass = Character::class
) {
    override val entityName = "Character"

    /**
     * Добавляет предмет в инвентарь персонажа.
     * Если предмет уже есть — увеличивает quantity.
     * Возвращает обновлённого персонажа.
     */
    fun addToInventory(characterId: Long, itemStock: ItemStock): Character {
        return transaction {
            val character = findById(characterId)
                ?: throw NotFoundException("Character(id=$characterId) not found")

            val inventory = character.inventory.toMutableSet()

            // Ищем существующий предмет в инвентаре
            val existing = inventory.find { it.item_id == itemStock.item_id }

            if (existing != null) {
                if ((existing.quantity + itemStock.quantity) < 0) throw BadRequestException("Character(id=$characterId) not enough item $itemStock he have $existing")
                existing.quantity += itemStock.quantity
            } else {
                if (itemStock.quantity <= 0) throw BadRequestException("Character(id=$characterId) item $itemStock quantity must be positive")
                inventory.add(itemStock)
            }

            // Не должно быть элементов с нулевым количеством
            inventory.removeAll { itm -> itm.quantity == 0 }

            // Обновляем JSONB-поле inventory
            val updated = CharacterTable.update({ (CharacterTable.id eq characterId) and (CharacterTable.version eq character.version) }) {
                it[CharacterTable.inventory] = inventory
                it[CharacterTable.version] = character.version + 1
            }

            //Проверка на оптимистичную блокировку
            if (updated == 0) throw OptimisticLockException(entityName, characterId, character.version)

            findById(characterId)!!
        }
    }
}
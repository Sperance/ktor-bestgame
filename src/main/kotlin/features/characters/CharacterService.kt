package features.characters

import application.model.ItemStock
import base.exception.NotFoundException
import base.service.BaseService
import features.items.ItemsRepository
import features.user.UserRepository

class CharacterService(
    private val characterRepo: CharacterRepository = CharacterRepository(),
    private val userRepo: UserRepository = UserRepository(),
    private val itemsRepo: ItemsRepository = ItemsRepository(),
) : BaseService<Character, CharacterTable>(characterRepo, Character.serializer()) {

    override fun entityName() = "Character"

    override fun validateCreate(entity: Character) {
        if (!userRepo.exists(entity.userId)) {
            throw NotFoundException("User(id=${entity.userId}) not found")
        }
    }

    /**
     * Добавляет предмет в инвентарь персонажа.
     * Проверяет, что item существует в таблице Items.
     */
    fun addItemToInventory(characterId: Long, itemStock: ItemStock): Character {
        // Проверяем, что такой предмет вообще существует
        if (!itemsRepo.exists(itemStock.item_id)) {
            throw NotFoundException("Item(id=${itemStock.item_id}) not found")
        }
        return characterRepo.addToInventory(characterId, itemStock)
    }
}
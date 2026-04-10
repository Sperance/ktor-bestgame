package features.characters

import application.model.ItemStock
import base.exception.NotFoundException
import base.service.BaseService
import extensions.printLog
import features.items.ItemsRepository
import features.user.UserRepository

class CharacterService(
    val characterRepo: CharacterRepository = CharacterRepository(),
    private val userRepo: UserRepository = UserRepository(),
) : BaseService<Character, CharacterTable>(characterRepo, Character.serializer()) {

    override fun entityName() = "Character"

    override fun validateCreate(entity: Character) {
        if (!userRepo.exists(entity.userId)) {
            throw NotFoundException("User(id=${entity.userId}) not found")
        }
    }

    fun addItemToInventory(characterId: Long, item: ItemStock) {
        val character = characterRepo.findById(characterId)!!
        printLog("[${this::class.simpleName}] Adding $item to ${character.name}' inventory")
    }
}
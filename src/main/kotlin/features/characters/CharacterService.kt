package features.characters

import base.exception.NotFoundException
import base.service.BaseService
import features.user.UserRepository

class CharacterService(
    characterRepo: CharacterRepository = CharacterRepository(),
    private val userRepo: UserRepository = UserRepository()
) : BaseService<Character, CharacterTable>(characterRepo, Character.serializer()) {

    override fun entityName() = "Character"

    override fun validateCreate(entity: Character) {
        if (!userRepo.exists(entity.userId)) {
            throw NotFoundException("User(id=${entity.userId}) not found")
        }
    }
}
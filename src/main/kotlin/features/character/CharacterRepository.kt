package features.character

import base.repository.BaseRepository
import base.repository.UniqueIndexConfig
import com.mongodb.kotlin.client.coroutine.ClientSession
import extensions.CONST_USER_MAX_CHARACTERS
import features.user.UserRepositoryMongo

object CharacterRepository : BaseRepository<Character>(
    entityClass = Character::class
) {
    init {
        initialize(uniqueIndexes = listOf(
            UniqueIndexConfig(
                indexName = "idx_unique_name",
                fields = listOf("name")
            )
        ))
    }

    override suspend fun validateBeforeInsert(entity: Character) {
        if (entity.name.isEmpty()) throw CharacterExceptions.funExceptionName("validateBeforeInsert")
        if (findByField(Character::name, entity.name) != null) throw CharacterExceptions.funExceptionNameDuplicate("validateBeforeInsert", entity.name)
        val findedUser = UserRepositoryMongo.findById(entity.userId)
        if (findedUser == null) throw CharacterExceptions.funExceptionUserNotFound("validateBeforeInsert", entity.userId)
        if (findedUser.countCharacters >= CONST_USER_MAX_CHARACTERS) throw CharacterExceptions.funExceptionMaxChars("validateBeforeInsert")
    }

    override suspend fun validateAfterInsert(entity: Character, session: ClientSession) {
        val findedUser = UserRepositoryMongo.findById(entity.userId)
        if (findedUser == null) throw CharacterExceptions.funExceptionUserNotFound("validateAfterInsert", entity.userId)
        findedUser.countCharacters++
        if (findedUser.countCharacters > CONST_USER_MAX_CHARACTERS) throw CharacterExceptions.funExceptionMaxChars("validateAfterInsert")
        UserRepositoryMongo.update(findedUser, session)
    }
}
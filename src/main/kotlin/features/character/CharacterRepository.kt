package features.character

import base.exception.ExceptionForCode
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
        if (entity.name.isEmpty()) throw ExceptionForCode("Нужно указать имя для нового персонажа", "CMR_VALIDINSERT_NAME")
        if (findByField(Character::name, entity.name) != null) throw ExceptionForCode("Персонаж с указанным именем существует", "CMR_VALIDATEINSERT_DUPLICATE")
        val findedUser = UserRepositoryMongo.findById(entity.userId)
        if (findedUser == null) throw ExceptionForCode("Не найден пользователь с ID ${entity.userId}", "CMR_VALIDATEINSERT_USER")
        if (findedUser.countCharacters >= CONST_USER_MAX_CHARACTERS) throw ExceptionForCode("У пользователя уже есть максимальное количество персонажей", "CMR_VALIDATEINSERT_MAX_CHARACTERS")
    }

    override suspend fun validateAfterInsert(entity: Character, session: ClientSession) {
        val findedUser = UserRepositoryMongo.findById(entity.userId)
        if (findedUser == null) throw ExceptionForCode("Не найден пользователь с ID ${entity.userId}", "CMR_VALIDATEINSERT_USER")
        findedUser.countCharacters++
        if (findedUser.countCharacters > CONST_USER_MAX_CHARACTERS) throw ExceptionForCode("У пользователя уже есть максимальное количество персонажей", "CMR_VALIDATEINSERT_MAX_CHARACTERS")
        UserRepositoryMongo.update(findedUser, session)
    }
}
package features.data.character

import base.repository.BaseRepository
import base.repository.UniqueIndexConfig
import base.route.ApiMongoResponse
import com.mongodb.client.model.Filters
import com.mongodb.kotlin.client.coroutine.ClientSession
import config.MongoFactory.transactionExecute
import extensions.CONST_FIELD_ID
import extensions.CONST_USER_MAX_CHARACTERS
import features.data.equipment.Equipment
import features.data.equipment.EquipmentRepository
import features.data.user.UserRepository
import org.bson.types.ObjectId
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue

class CharacterRepository : BaseRepository<Character>(
    entityClass = Character::class
), KoinComponent {
    val userRepository: UserRepository by inject()
    val equipmentRepository: EquipmentRepository by inject()

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
        val findedUser = userRepository.findById(entity.userId)
        if (findedUser == null) throw CharacterExceptions.funExceptionUserNotFound("validateBeforeInsert", entity.userId)
        if (findedUser.countCharacters >= CONST_USER_MAX_CHARACTERS) throw CharacterExceptions.funExceptionMaxChars("validateBeforeInsert")
    }

    override suspend fun validateAfterInsert(entity: Character, session: ClientSession) {
        val findedUser = userRepository.findById(entity.userId)
        if (findedUser == null) throw CharacterExceptions.funExceptionUserNotFound("validateAfterInsert", entity.userId)
        findedUser.countCharacters++
        if (findedUser.countCharacters > CONST_USER_MAX_CHARACTERS) throw CharacterExceptions.funExceptionMaxChars("validateAfterInsert")
        userRepository.update(findedUser, session)
    }

    /*****/

    suspend fun getEquipmentsData(characterId: String): List<Equipment> {
        return equipmentRepository.findByFilter(Filters.eq("characterId", characterId))
    }

    suspend fun getEquippedData(characterId: String): List<Equipment> {
        val character = findById(characterId)
        if (character == null) throw CharacterExceptions.funExceptionNotFound("getEquippedData", characterId)
        val mapIdEquipments = character.equipments.map { it.equipmentId }
        if (mapIdEquipments.isEmpty()) return emptyList()
        return equipmentRepository.findByFilter(
            Filters.and(
                Filters.`in`(CONST_FIELD_ID, mapIdEquipments.map { ObjectId(it) }),
                Filters.eq("characterId", characterId)
            )
        )
    }

    /*****/

    /**
     * Добавление нового предмета в инвентарь персонажа. Создание предмета
     */
    suspend fun itemToInventory(characterId: String, item: CharacterEquipments): String {
        val character = findById(characterId)
        if (character == null) throw CharacterExceptions.funExceptionNotFound("itemToInventory", characterId)
        if (equipmentRepository.findById(item.equipmentId) == null) throw CharacterExceptions.funExceptionItemNotFound("itemToInventory", item.equipmentId)

        character.equipments.add(item)
        transactionExecute { session ->
            update(character, session)
        }
        return "Success"
    }
}
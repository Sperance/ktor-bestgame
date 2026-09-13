package ru.descend.features.character.persistence

import com.mongodb.client.model.Filters
import com.mongodb.kotlin.client.coroutine.ClientSession
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import ru.descend.features.character.model.Character
import ru.descend.features.character.model.CharacterEquipments
import ru.descend.features.character.model.CharacterItems
import ru.descend.features.equipment.model.Equipment
import ru.descend.features.equipment.persistence.EquipmentRepository
import ru.descend.features.items.persistence.ItemsRepository
import ru.descend.features.redemptioncodes.persistence.RedemptionCodesRepository
import ru.descend.features.user.model.User
import ru.descend.features.user.persistence.UserRepository
import ru.descend.infrastructure.cache.ItemsCache
import ru.descend.infrastructure.mongo.BaseRepository
import ru.descend.infrastructure.mongo.MongoFactory.transactionExecute
import ru.descend.infrastructure.mongo.UniqueIndexConfig
import ru.descend.shared.CONST_FIELD_ID
import ru.descend.shared.CONST_USER_MAX_CHARACTERS
import ru.descend.shared.error.model.CharacterExceptions

class CharacterRepository : BaseRepository<Character>(
    entityClass = Character::class
), KoinComponent {
    val userRepository: UserRepository by inject()
    val equipmentRepository: EquipmentRepository by inject()
    val itemsRepository: ItemsRepository by inject()
    val redemptionCodesRepository: RedemptionCodesRepository by inject()
    val itemsCache: ItemsCache by inject()
    private val modifierCatalogs: ru.descend.features.poe.persistence.MongoModifierCatalog by inject()

    init {
        initialize(uniqueIndexes = listOf(
            UniqueIndexConfig(
                indexName = "idx_unique_name",
                fields = listOf("name")
            )
        ))
    }

    override suspend fun validateBeforeInsert(entity: Character, session: ClientSession) {
        if (entity.name.isEmpty()) throw CharacterExceptions.funExceptionName("validateBeforeInsert")
        if (findByField(Character::name, entity.name) != null) throw CharacterExceptions.funExceptionNameDuplicate("validateBeforeInsert", entity.name)
        val findedUser = userRepository.findByField(User::_id, entity.userId, session)
        if (findedUser == null) throw CharacterExceptions.funExceptionUserNotFound("validateBeforeInsert", entity.userId)
        if (findedUser.countCharacters >= CONST_USER_MAX_CHARACTERS) throw CharacterExceptions.funExceptionMaxChars("validateBeforeInsert")
    }

    override suspend fun validateAfterInsert(entity: Character, session: ClientSession) {
        val findedUser = userRepository.findByField(User::_id, entity.userId, session)
        if (findedUser == null) throw CharacterExceptions.funExceptionUserNotFound("validateAfterInsert", entity.userId)
        findedUser.countCharacters++
        if (findedUser.countCharacters > CONST_USER_MAX_CHARACTERS) throw CharacterExceptions.funExceptionMaxChars("validateAfterInsert")
        userRepository.update(findedUser, session)
    }

    suspend fun getEquipmentsData(characterId: String): List<Equipment> {
        val character = findById(characterId)
            ?: throw CharacterExceptions.funExceptionNotFound("getEquipmentsData", characterId)
        val ids = character.equipments.map { it.equipmentId }
        return if (ids.isEmpty()) emptyList() else equipmentRepository.findByFilter(Filters.`in`(CONST_FIELD_ID, ids))
    }

    suspend fun getEquippedData(characterId: String): List<Equipment> {
        val character = findById(characterId) ?: ru.descend.shared.http.missing()
        return character.equipments.filter { it.uuid in character.equipped.values }.map {
            it.baseSnapshot ?: equipmentRepository.findById(it.equipmentId) ?: ru.descend.shared.http.missing()
        }
    }

    /**
     * Добавление нового предмета в инвентарь персонажа. Создание предмета
     */
    @Deprecated("Use EquipmentService.grant with Actor and expectedVersion")
    suspend fun itemToInventory(characterId: String, item: CharacterEquipments): String =
        ru.descend.shared.http.invalid("Use the authenticated grant command")

    @Deprecated("Use InventoryCommandService.adjust with Actor and expectedVersion")
    suspend fun addItem(characterId: String, itemObj: List<CharacterItems>): String =
        ru.descend.shared.http.invalid("Use the authenticated inventory command")
}

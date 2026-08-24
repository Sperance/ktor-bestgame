package features.data.character

import base.exception.model.CharacterExceptions
import base.repository.BaseRepository
import base.repository.UniqueIndexConfig
import com.mongodb.client.model.Filters
import com.mongodb.kotlin.client.coroutine.ClientSession
import config.MongoFactory.transactionExecute
import CONST_FIELD_ID
import CONST_USER_MAX_CHARACTERS
import features.caches.ItemsCache
import features.data.equipment.Equipment
import features.data.user.UserRepository
import org.bson.types.ObjectId
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue

class CharacterRepository : BaseRepository<Character>(
    entityClass = Character::class
), KoinComponent {
    val userRepository: UserRepository by inject()
    val equipmentRepository: features.data.equipment.EquipmentRepository by inject()
    val itemsRepository: features.data.items.ItemsRepository by inject()
    val itemsCache: features.caches.ItemsCache by inject()

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
        val findedUser = userRepository.findById(entity.userId, session)
        if (findedUser == null) throw CharacterExceptions.funExceptionUserNotFound("validateBeforeInsert", entity.userId.toHexString())
        if (findedUser.countCharacters >= CONST_USER_MAX_CHARACTERS) throw CharacterExceptions.funExceptionMaxChars("validateBeforeInsert")
    }

    override suspend fun validateAfterInsert(entity: Character, session: ClientSession) {
        val findedUser = userRepository.findById(entity.userId, session)
        if (findedUser == null) throw CharacterExceptions.funExceptionUserNotFound("validateAfterInsert", entity.userId.toHexString())
        findedUser.countCharacters++
        if (findedUser.countCharacters > CONST_USER_MAX_CHARACTERS) throw CharacterExceptions.funExceptionMaxChars("validateAfterInsert")
        userRepository.update(findedUser, session)
    }

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

    /**
     * Добавление нового предмета в инвентарь персонажа. Создание предмета
     */
    suspend fun itemToInventory(characterId: String, item: CharacterEquipments): String {
        val character = findById(characterId)
        if (character == null) throw CharacterExceptions.funExceptionNotFound("itemToInventory", characterId)
        if (equipmentRepository.findById(item.equipmentId) == null) throw CharacterExceptions.funExceptionItemNotFound("itemToInventory", item.equipmentId)

        character.equipments.add(item)
        transactionExecute("itemToInventory") { session ->
            update(character, session)
        }
        return "Success"
    }

    /**
     * Добавление\удаление предмета из инвентаря персонажа
     */
    suspend fun addItem(characterId: String, itemObj: List<CharacterItems>): String {
        val character = findById(characterId)
        if (character == null) throw CharacterExceptions.funExceptionNotFound("addItem", characterId)

        val allItems = itemsCache.getCache()

        var isChanged = false
        itemObj.forEach { itm ->
            if (itm.amount == 0L) return@forEach
            if (itm.amount > 100000000L) throw CharacterExceptions.funExceptionItemOverAmount("addItem", itm.toString())
            if (itm.amount < 100000000L) throw CharacterExceptions.funExceptionItemOverAmount("addItem", itm.toString())
            if (allItems.find { it.getId() == itm.itemId } == null) throw CharacterExceptions.funExceptionItemNotFound("addItem", itm.toString())

            val findedItem = character.items.find { it.itemId == itm.itemId }
            if (findedItem != null) {
                findedItem.amount += itm.amount
                if (findedItem.amount < 0) throw CharacterExceptions.funExceptionItemLowZero("addItem", itm.toString())
            }
            else {
                if (itm.amount <= 0) throw CharacterExceptions.funExceptionItemLowZero("addItem", itm.toString())
                character.items.add(CharacterItems(itm.itemId, itm.amount))
            }

            isChanged = true
        }

        if (!isChanged) {
            return "Success. No changes"
        }

        //Зачем хранить id предмета без кол-ва
        character.items.removeAll { it.amount == 0L }

        transactionExecute("addItem") { session ->
            update(character, session)
        }
        return "Success"
    }
}
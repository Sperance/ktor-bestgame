package features.data.character

import application.enums.IntEnumStat
import base.exception.model.CharacterExceptions
import base.repository.BaseRepository
import base.repository.UniqueIndexConfig
import com.mongodb.kotlin.client.coroutine.ClientSession
import config.MongoFactory.transactionExecute
import CONST_ITEM_MAX_AMOUNT
import CONST_USER_MAX_CHARACTERS
import features.caches.ItemsCache
import features.data.character.character_data.CharacterItems
import features.data.character.character_data.toStorage
import features.data.equipment.EquipmentRepository
import features.data.inventory.CharacterEquipment
import features.data.inventory.CharacterEquipmentRepository
import features.data.items.ItemsRepository
import features.data.redemptionCodes.RedemptionCodesRepository
import features.data.user.User
import features.data.user.UserRepository
import features.logic.modifiers.ModifierCalculator
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue

class CharacterRepository : BaseRepository<Character>(
    entityClass = Character::class
), KoinComponent {
    val userRepository: UserRepository by inject()
    val equipmentRepository: EquipmentRepository by inject()
    val characterEquipmentRepository: CharacterEquipmentRepository by inject()
    val itemsRepository: ItemsRepository by inject()
    val redemptionCodesRepository: RedemptionCodesRepository by inject()
    val itemsCache: ItemsCache by inject()

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

    override suspend fun validateAfterDelete(entity: Character, session: ClientSession, softDelete: Boolean) {
        if (softDelete) return
        characterEquipmentRepository.deleteByCharacter(entity._id, session)
    }

    /**
     * Весь инвентарь экипировки персонажа - отдельные документы коллекции `CharacterEquipment`.
     */
    suspend fun getEquipmentsData(characterId: String): List<CharacterEquipment> {
        if (findById(characterId) == null) throw CharacterExceptions.funExceptionNotFound("getEquipmentsData", characterId)
        return characterEquipmentRepository.findByCharacter(characterId)
    }

    /**
     * Надетая экипировка персонажа.
     */
    suspend fun getEquippedData(characterId: String): List<CharacterEquipment> {
        if (findById(characterId) == null) throw CharacterExceptions.funExceptionNotFound("getEquippedData", characterId)
        return characterEquipmentRepository.findEquipped(characterId)
    }

    /**
     * Итоговые характеристики персонажа с учётом надетой экипировки.
     *
     * База берётся из stockSkills персонажа, поверх неё сводятся модификаторы
     * самого персонажа и всех надетых предметов по правилам [ModifierCalculator].
     */
    suspend fun calculateStats(characterId: String): Map<IntEnumStat, Double> {
        val character = findById(characterId)
            ?: throw CharacterExceptions.funExceptionNotFound("calculateStats", characterId)

        val equipped = characterEquipmentRepository.findEquipped(characterId)
        val modifiers = character.params + equipped.flatMap { it.params }
        val base = character.stockSkills.associate { it.stat as IntEnumStat to it.value.toDouble() }

        return ModifierCalculator.calculate(modifiers, base)
    }

    /**
     * Добавление нового предмета экипировки в инвентарь персонажа.
     *
     * Создаёт отдельный документ инвентаря с зароленными под шаблон модификаторами.
     */
    suspend fun itemToInventory(characterId: String, equipmentId: String): CharacterEquipment {
        if (findById(characterId) == null) throw CharacterExceptions.funExceptionNotFound("itemToInventory", characterId)
        val equipment = equipmentRepository.findById(equipmentId)
            ?: throw CharacterExceptions.funExceptionEquipmentNotFound("itemToInventory", equipmentId)

        return transactionExecute("itemToInventory") { session ->
            characterEquipmentRepository.addFromEquipment(characterId, equipment, session)
        }
    }

    /**
     * Добавление\удаление простого предмета из инвентаря персонажа.
     *
     * Простые предметы лежат плоским массивом строк "itemId:amount",
     * поэтому изменения применяются к разобранному списку и сворачиваются обратно.
     */
    suspend fun addItem(characterId: String, itemObj: List<CharacterItems>): String {
        val character = findById(characterId)
        if (character == null) throw CharacterExceptions.funExceptionNotFound("addItem", characterId)

        val allItems = itemsCache.getCache()
        val currentItems = character.parseItems()

        var isChanged = false
        itemObj.forEach { itm ->
            if (itm.amount == 0L) return@forEach
            if (itm.amount > CONST_ITEM_MAX_AMOUNT) throw CharacterExceptions.funExceptionItemOverAmount("addItem", itm.toString())
            if (itm.amount < -CONST_ITEM_MAX_AMOUNT) throw CharacterExceptions.funExceptionItemOverAmount("addItem", itm.toString())
            if (allItems.find { it._id == itm.itemId } == null) throw CharacterExceptions.funExceptionItemNotFound("addItem", itm.toString())

            val findedItem = currentItems.find { it.itemId == itm.itemId }
            if (findedItem != null) {
                findedItem.amount += itm.amount
                if (findedItem.amount < 0) throw CharacterExceptions.funExceptionItemLowZero("addItem", itm.toString())
            }
            else {
                if (itm.amount <= 0) throw CharacterExceptions.funExceptionItemLowZero("addItem", itm.toString())
                currentItems.add(CharacterItems(itm.itemId, itm.amount))
            }

            isChanged = true
        }

        if (!isChanged) {
            return "Success. No changes"
        }

        //Зачем хранить id предмета без кол-ва
        currentItems.removeAll { it.amount == 0L }
        character.items = currentItems.toStorage()

        transactionExecute("addItem") { session ->
            update(character, session)
        }
        return "Success"
    }
}

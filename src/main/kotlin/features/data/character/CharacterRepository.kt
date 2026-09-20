package features.data.character

import base.exception.model.CharacterExceptions
import base.exception.model.ProgressionExceptions
import base.repository.BaseRepository
import base.repository.UniqueIndexConfig
import com.mongodb.kotlin.client.coroutine.ClientSession
import config.MongoFactory.transactionExecute
import CONST_ITEM_MAX_AMOUNT
import CONST_USER_MAX_CHARACTERS
import features.caches.CharacterClassCache
import features.caches.ExperienceLevelCache
import features.caches.ItemsCache
import features.data.character.character_data.CharacterItems
import features.data.character.character_data.toStorage
import features.data.equipment.EquipmentRepository
import features.data.inventory.CharacterEquipment
import features.data.inventory.CharacterEquipmentRepository
import features.data.items.ItemsRepository
import features.data.skilltree.CharacterSkillNodeRepository
import features.data.redemptionCodes.RedemptionCodesRepository
import features.data.user.User
import features.data.user.UserRepository
import features.logic.progression.CharacterClass
import features.logic.stats.CharacterStats
import features.logic.stats.CharacterStatsCalculator
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue

class CharacterRepository : BaseRepository<Character>(
    entityClass = Character::class
), KoinComponent {
    val userRepository: UserRepository by inject()
    val equipmentRepository: EquipmentRepository by inject()
    val characterEquipmentRepository: CharacterEquipmentRepository by inject()
    val characterSkillNodeRepository: CharacterSkillNodeRepository by inject()
    val itemsRepository: ItemsRepository by inject()
    val redemptionCodesRepository: RedemptionCodesRepository by inject()
    val itemsCache: ItemsCache by inject()
    val characterClassCache: CharacterClassCache by inject()
    val experienceLevelCache: ExperienceLevelCache by inject()

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
        if (characterClassCache.findById(entity.classId) == null)
            throw ProgressionExceptions.funExceptionClassNotFound("validateBeforeInsert", entity.classId)
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

        // Стартовый узел дерева выдаётся сразу: в POE класс приходит в дерево
        // со своей точки входа, она бесплатна и отдельного выбора не требует
        characterSkillNodeRepository.allocateStart(entity, requireClass(entity), session)
    }

    override suspend fun validateAfterDelete(entity: Character, session: ClientSession, softDelete: Boolean) {
        if (softDelete) return
        characterEquipmentRepository.deleteByCharacter(entity._id, session)
        characterSkillNodeRepository.deleteByCharacter(entity._id, session)
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
     * Списывает у персонажа простые предметы в рамках уже открытой транзакции.
     *
     * Нужен операциям, которые тратят предмет и тут же меняют что-то ещё -
     * например применению валютной сферы.
     *
     * @throws CharacterExceptions.CharacterException если предмета не хватает
     */
    suspend fun spendItem(character: Character, itemId: String, amount: Long, session: ClientSession) {
        if (amount <= 0) throw CharacterExceptions.funExceptionItemLowZero("spendItem", "$itemId:$amount")

        val items = character.parseItems()
        val owned = items.find { it.itemId == itemId }
        if (owned == null || owned.amount < amount)
            throw CharacterExceptions.funExceptionItemLowZero("spendItem", "$itemId:${owned?.amount ?: 0}")

        owned.amount -= amount
        items.removeAll { it.amount == 0L }
        character.items = items.toStorage()

        update(character, session)
    }

    /**
     * Итоговые характеристики персонажа: база класса на его уровне,
     * дерево навыков и работающая экипировка.
     *
     * Единственная точка расчёта - боёвка и любые другие механики должны
     * брать характеристики отсюда, иначе потеряется чей-нибудь источник
     * или будет учтён предмет, который на самом деле не работает.
     */
    suspend fun calculateStats(characterId: String): CharacterStats {
        val character = findById(characterId)
            ?: throw CharacterExceptions.funExceptionNotFound("calculateStats", characterId)

        return CharacterStatsCalculator.calculate(
            character = character,
            characterClass = requireClass(character),
            skillNodes = characterSkillNodeRepository.findByCharacter(characterId),
            equipped = characterEquipmentRepository.findEquipped(characterId)
        )
    }

    /**
     * Класс персонажа из справочника.
     */
    fun requireClass(character: Character): CharacterClass =
        characterClassCache.findById(character.classId)
            ?: throw ProgressionExceptions.funExceptionClassNotFound("requireClass", character.classId)

    /**
     * Сколько очков дерева навыков персонаж накопил к своему уровню.
     */
    fun skillPointsTotal(character: Character): Int =
        experienceLevelCache.skillPointsUpTo(character.level.toInt())

    /**
     * Начисляет опыт и поднимает уровень, если пройден очередной порог.
     *
     * Уровень только растёт: потеря опыта не должна забирать уже
     * вложенные очки дерева.
     */
    suspend fun addExperience(characterId: String, amount: Double): Character {
        val character = findById(characterId)
            ?: throw CharacterExceptions.funExceptionNotFound("addExperience", characterId)
        if (amount < 0) throw CharacterExceptions.funExceptionExperience("addExperience", amount.toString())
        if (experienceLevelCache.isEmpty()) throw ProgressionExceptions.funExceptionNoLevels("addExperience")

        character.experience += amount
        val reached = experienceLevelCache.levelOf(character.experience)
        if (reached > character.level) character.level = reached.toShort()

        transactionExecute("addExperience") { session ->
            update(character, session)
        }
        return character
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

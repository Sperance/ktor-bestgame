package features.data.character

import application.enums.EnumCurrencyOrb
import application.enums.EnumSkillNodeType
import base.exception.model.CharacterExceptions
import base.exception.model.ProgressionExceptions
import base.exception.model.SkillTreeExceptions
import base.repository.BaseRepository
import base.repository.UniqueIndexConfig
import com.mongodb.client.model.Filters
import com.mongodb.kotlin.client.coroutine.ClientSession
import config.MongoFactory.transactionExecute
import extensions.toStableObjectId
import CONST_ITEM_MAX_AMOUNT
import CONST_USER_MAX_CHARACTERS
import features.caches.CharacterClassCache
import features.caches.ExperienceLevelCache
import features.caches.ItemsCache
import features.caches.SkillTreeCache
import features.data.character.character_data.CharacterItems
import features.data.character.character_data.CharacterSkillNode
import features.data.character.character_data.toStorage
import features.data.equipment.EquipmentRepository
import features.data.inventory.CharacterEquipment
import features.data.inventory.CharacterEquipmentRepository
import features.data.items.ItemsRepository
import features.data.redemptionCodes.RedemptionCodesRepository
import features.data.user.User
import features.data.user.UserRepository
import features.logic.progression.CharacterClass
import features.logic.skilltree.CharacterSkillTreeState
import features.logic.skilltree.SkillTreeAllocation
import features.logic.skilltree.SkillTreeNode
import features.logic.stats.CharacterStats
import features.logic.modifiers.ModifierCalculator
import features.logic.stats.CharacterStatsCalculator
import org.bson.Document
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
    val characterClassCache: CharacterClassCache by inject()
    val skillTreeCache: SkillTreeCache by inject()
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
        // Имя уникально в индексе, поэтому занятым считается и имя мягко удалённого персонажа
        if (findByField(Character::name, entity.name, includeDeleted = true) != null) throw CharacterExceptions.funExceptionNameDuplicate("validateBeforeInsert", entity.name)
        val findedUser = userRepository.findByField(User::_id, entity.userId, session)
        if (findedUser == null) throw CharacterExceptions.funExceptionUserNotFound("validateBeforeInsert", entity.userId)
        if (findedUser.countCharacters >= CONST_USER_MAX_CHARACTERS) throw CharacterExceptions.funExceptionMaxChars("validateBeforeInsert")

        // Стартовый узел дерева ставится сразу: в POE класс приходит в дерево
        // со своей точки входа, она бесплатна и отдельного выбора не требует
        if (entity.skillNodes.isEmpty()) entity.skillNodes.add(startNodeOf(entity, "validateBeforeInsert"))
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
     * Персонажи одного игрока - то, из чего он выбирает при входе.
     *
     * Их не больше [CONST_USER_MAX_CHARACTERS], поэтому запрос отдаёт список целиком,
     * без страниц. Игрок не должен вычитывать коллекцию целиком ради своих трёх:
     * чужие персонажи за пределы сервера не уезжают.
     */
    suspend fun findByUser(userId: String): List<Character> {
        if (userRepository.findByField(User::_id, userId) == null)
            throw CharacterExceptions.funExceptionUserNotFound("findByUser", userId)

        return findByFilter(Filters.eq("userId", userId))
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
     * Выдаёт персонажу простые предметы в рамках уже открытой транзакции.
     *
     * Обратная сторона [spendItem]: нужна операциям, которые перекладывают
     * предметы между персонажами и обязаны уложиться в одну транзакцию -
     * например покупке на аукционе.
     *
     * @throws CharacterExceptions.CharacterException если предмета нет в справочнике
     * или его станет больше допустимого
     */
    suspend fun earnItem(character: Character, itemId: String, amount: Long, session: ClientSession) {
        if (amount <= 0) throw CharacterExceptions.funExceptionItemLowZero("earnItem", "$itemId:$amount")
        if (itemsCache.findById(itemId) == null) throw CharacterExceptions.funExceptionItemNotFound("earnItem", itemId)

        val items = character.parseItems()
        val owned = items.find { it.itemId == itemId }

        if (owned == null) {
            items.add(CharacterItems(itemId, amount))
        } else {
            owned.amount += amount
            if (owned.amount > CONST_ITEM_MAX_AMOUNT)
                throw CharacterExceptions.funExceptionItemOverAmount("earnItem", "$itemId:${owned.amount}")
        }

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
        val character = requireCharacter(characterId, "calculateStats")

        return CharacterStatsCalculator.calculate(
            character = character,
            characterClass = requireClass(character),
            skillNodes = character.skillNodes,
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

    // ==================== Дерево навыков ====================

    /**
     * Состояние дерева навыков персонажа: взятые узлы и баланс очков.
     */
    suspend fun skillTreeState(characterId: String): CharacterSkillTreeState =
        stateOf(requireCharacter(characterId, "skillTreeState"))

    /**
     * Берёт узел дерева.
     *
     * @throws SkillTreeExceptions.SkillTreeException если узел брать нельзя
     */
    suspend fun allocateSkillNode(characterId: String, nodeCode: String): CharacterSkillTreeState {
        val character = requireCharacter(characterId, "allocateSkillNode")
        val node = requireNode(nodeCode, "allocateSkillNode")

        val available = stateOf(character).available

        SkillTreeAllocation.requireAllocatable(
            nodes = skillTreeCache.getCache(),
            node = node,
            taken = character.skillNodes.map { it.code },
            startNodeCode = requireClass(character).startNodeCode,
            available = available
        )

        character.skillNodes.add(CharacterSkillNode.fromNode(node))
        transactionExecute("allocateSkillNode $nodeCode") { session -> update(character, session) }

        return stateOf(character)
    }

    /**
     * Откатывает узел и возвращает очки.
     *
     * Стартовый узел откатывается только полным сбросом: без него дерево
     * теряет корень.
     *
     * @throws SkillTreeExceptions.SkillTreeException если узел откатить нельзя
     */
    suspend fun refundSkillNode(characterId: String, nodeCode: String): CharacterSkillTreeState {
        val character = requireCharacter(characterId, "refundSkillNode")
        val node = requireNode(nodeCode, "refundSkillNode")

        SkillTreeAllocation.requireRefundable(skillTreeCache.getCache(), node, character.skillNodes.map { it.code })
        SkillTreeAllocation.requireSocketEmpty(node, socketedNodes(characterId))

        // Возврат стоит сферу сожаления. Списание и сам возврат идут одной
        // транзакцией: иначе неудачная запись оставила бы игрока без сферы и с узлом.
        spendRegret(character, count = 1, method = "refundSkillNode")

        character.skillNodes.removeAll { it.code == node.code }
        transactionExecute("refundSkillNode $nodeCode") { session -> update(character, session) }

        return stateOf(character)
    }

    /**
     * Полный сброс дерева: все очки возвращаются, персонаж остаётся на
     * стартовом узле своего класса - как после респека в POE.
     */
    suspend fun resetSkillTree(characterId: String): CharacterSkillTreeState {
        val character = requireCharacter(characterId, "resetSkillTree")

        // Ни одно гнездо не должно остаться занятым: камень повис бы в узле,
        // которого у персонажа после сброса нет.
        val socketed = socketedNodes(characterId)
        skillTreeCache.getCache()
            .filter { it.code in character.skillNodes.map { taken -> taken.code } }
            .forEach { SkillTreeAllocation.requireSocketEmpty(it, socketed) }

        // Сброс стоит по сфере за каждый возвращаемый узел: стартовый бесплатен,
        // потому что он и не возвращается. Так полный сброс не дешевле поузлового,
        // и выбор между ними остаётся выбором удобства, а не цены.
        val returned = character.skillNodes.count { it.type != EnumSkillNodeType.START }
        if (returned > 0) spendRegret(character, count = returned, method = "resetSkillTree")

        character.skillNodes = mutableListOf(startNodeOf(character, "resetSkillTree"))
        transactionExecute("resetSkillTree") { session -> update(character, session) }

        return stateOf(character)
    }

    /**
     * Коды гнёзд, в которых у персонажа сейчас сидят самоцветы.
     */
    private suspend fun socketedNodes(characterId: String): Set<String> =
        characterEquipmentRepository.findByCharacter(characterId)
            .mapNotNullTo(mutableSetOf()) { it.socketCode }

    /**
     * Списывает сферы сожаления из сумки персонажа.
     *
     * Сумка меняется здесь же, в документе персонажа: дерево и сумка - поля одного
     * документа, поэтому одна запись закрывает и то, и другое.
     *
     * @throws SkillTreeExceptions.SkillTreeException если сфер не хватает
     */
    private fun spendRegret(character: Character, count: Int, method: String) {
        val orbId = EnumCurrencyOrb.ORB_OF_REGRET.name.toStableObjectId()
        val items = character.parseItems()
        val owned = items.find { it.itemId == orbId }

        if (owned == null || owned.amount < count)
            throw SkillTreeExceptions.funExceptionNoRegret(method, "$count, have ${owned?.amount ?: 0}")

        owned.amount -= count
        items.removeAll { it.amount <= 0L }
        character.items = items.toStorage()
    }

    /**
     * Выдаёт стартовый узел класса тем персонажам, у которых дерево пустое.
     *
     * Новым узел ставится при создании, этот шаг чинит созданных раньше и тех,
     * у кого узлы вычистила перебалансировка дерева.
     *
     * @return сколько персонажей получили стартовый узел
     */
    suspend fun ensureStartNodes(session: ClientSession): Int {
        val orphans = findAll(session).filter { it.skillNodes.isEmpty() }

        orphans.forEach { character ->
            character.skillNodes.add(startNodeOf(character, "ensureStartNodes"))
            update(character, session)
        }

        return orphans.size
    }

    /**
     * Убирает у всех персонажей узлы, которых в дереве больше нет.
     *
     * Очки за них возвращаются сами: они считаются от уровня персонажа,
     * а не хранятся отдельно.
     *
     * @param nodeCodes коды всех существующих узлов дерева
     * @return сколько персонажей потеряли хотя бы один узел
     */
    suspend fun pruneMissingSkillNodes(nodeCodes: Collection<String>, session: ClientSession): Long {
        if (nodeCodes.isEmpty()) return 0

        // $pull по коду каждого снимка: modifiedCount посчитает
        // только тех персонажей, у кого действительно что-то убралось
        val stale = Document("code", Document("\$nin", nodeCodes.toList()))
        val pull = Document("\$pull", Document("skillNodes", stale))

        return collection.updateMany(session, Filters.empty(), pull).modifiedCount
    }

    private fun stateOf(character: Character): CharacterSkillTreeState {
        val total = skillPointsTotal(character)
        // Стоимость берётся из снимка: именно столько персонаж за узел заплатил
        val spent = character.skillNodes.sumOf { it.cost }

        return CharacterSkillTreeState(
            characterId = character._id,
            total = total,
            spent = spent,
            available = total - spent,
            // Копия: состояние - это ответ наружу, а не окно в документ персонажа
            nodes = character.skillNodes.toList(),
            // Вклад дерева, а не итог персонажа: базы здесь нет, поэтому проценты
            // остаются процентами. Свод делает тот же калькулятор - складывать
            // операции разных видов на клиенте было бы неверно.
            totals = ModifierCalculator.contributions(
                ModifierCalculator.expand(character.skillNodes.flatMap { it.params })
            )
        )
    }

    /**
     * Снимок стартового узла класса персонажа.
     */
    private fun startNodeOf(character: Character, method: String): CharacterSkillNode =
        CharacterSkillNode.fromNode(requireNode(requireClass(character).startNodeCode, method))

    private suspend fun requireCharacter(characterId: String, method: String): Character =
        findById(characterId) ?: throw CharacterExceptions.funExceptionNotFound(method, characterId)

    private fun requireNode(nodeCode: String, method: String): SkillTreeNode =
        skillTreeCache.findByCode(nodeCode)
            ?: throw SkillTreeExceptions.funExceptionNodeNotFound(method, nodeCode)

    /**
     * Начисляет опыт и поднимает уровень, если пройден очередной порог.
     *
     * Уровень только растёт: потеря опыта не должна забирать уже
     * вложенные очки дерева.
     */
    suspend fun addExperience(characterId: String, amount: Double): Character {
        val character = requireCharacter(characterId, "addExperience")
        applyExperience(character, amount, "addExperience")

        transactionExecute("addExperience") { session ->
            update(character, session)
        }
        return character
    }

    /**
     * Опыт на уже прочитанном персонаже, без записи.
     *
     * Существует отдельно от [addExperience] ради того, кто начисляет опыт не один:
     * промокод выдаёт опыт, золото и предметы одной транзакцией, и своя транзакция
     * внутри каждого начисления сделала бы выдачу делимой - половина награды при
     * сбое хуже, чем её отсутствие.
     */
    fun applyExperience(character: Character, amount: Double, method: String) {
        if (amount < 0) throw CharacterExceptions.funExceptionExperience(method, amount.toString())
        if (experienceLevelCache.isEmpty()) throw ProgressionExceptions.funExceptionNoLevels(method)

        character.experience += amount
        val reached = experienceLevelCache.levelOf(character.experience)
        if (reached > character.level) character.level = reached.toShort()
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

        if (!applyItems(character, itemObj, "addItem")) return "system.no_changes"

        transactionExecute("addItem") { session ->
            update(character, session)
        }
        return "system.success"
    }

    /**
     * Простые предметы на уже прочитанном персонаже, без записи.
     *
     * Отвечает, изменилось ли что-нибудь: нулевое количество - не ошибка, а просто
     * отсутствие работы. Существует отдельно от [addItem] по той же причине, что и
     * [applyExperience]: выдача нескольких наград должна быть одной транзакцией.
     */
    fun applyItems(character: Character, itemObj: List<CharacterItems>, method: String): Boolean {
        val allItems = itemsCache.getCache()
        val currentItems = character.parseItems()

        var isChanged = false
        itemObj.forEach { itm ->
            if (itm.amount == 0L) return@forEach
            if (itm.amount > CONST_ITEM_MAX_AMOUNT) throw CharacterExceptions.funExceptionItemOverAmount(method, itm.toString())
            if (itm.amount < -CONST_ITEM_MAX_AMOUNT) throw CharacterExceptions.funExceptionItemOverAmount(method, itm.toString())
            if (allItems.find { it._id == itm.itemId } == null) throw CharacterExceptions.funExceptionItemNotFound(method, itm.toString())

            val findedItem = currentItems.find { it.itemId == itm.itemId }
            if (findedItem != null) {
                findedItem.amount += itm.amount
                if (findedItem.amount < 0) throw CharacterExceptions.funExceptionItemLowZero(method, itm.toString())
            }
            else {
                if (itm.amount <= 0) throw CharacterExceptions.funExceptionItemLowZero(method, itm.toString())
                currentItems.add(CharacterItems(itm.itemId, itm.amount))
            }

            isChanged = true
        }

        if (!isChanged) return false

        //Зачем хранить id предмета без кол-ва
        currentItems.removeAll { it.amount == 0L }
        character.items = currentItems.toStorage()
        return true
    }
}

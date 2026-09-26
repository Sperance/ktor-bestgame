package features.data.character

import application.enums.EnumCurrencyOrb
import application.enums.EnumSkillNodeType
import base.exception.model.AuthExceptions
import base.exception.model.CharacterExceptions
import features.logic.auth.caller
import base.exception.model.ProgressionExceptions
import base.exception.model.SkillTreeExceptions
import base.repository.BaseRepository
import base.repository.IndexSpec
import com.mongodb.client.model.Filters
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.Projections
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Updates
import extensions.now
import kotlinx.datetime.LocalDateTime
import features.caches.EquipmentCache
import features.caches.ModifierDefinitionCache
import features.logic.hero.heroChanges
import features.logic.stats.StatsMemo
import com.mongodb.kotlin.client.coroutine.ClientSession
import config.MongoFactory.transactionExecute
import extensions.toStableObjectId
import CONST_FIELD_VERSION
import CONST_ITEM_MAX_AMOUNT
import CONST_USER_MAX_CHARACTERS
import features.caches.CharacterClassCache
import features.caches.ExperienceLevelCache
import features.caches.ItemsCache
import features.caches.SkillTreeCache
import features.data.character.character_data.CharacterItems
import features.data.character.character_data.CharacterSkillNode
import features.data.auction.AuctionLotRepository
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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
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
    private val auctionLotRepository: AuctionLotRepository by inject()
    val itemsCache: ItemsCache by inject()
    val characterClassCache: CharacterClassCache by inject()
    val skillTreeCache: SkillTreeCache by inject()
    val experienceLevelCache: ExperienceLevelCache by inject()
    private val equipmentCache: EquipmentCache by inject()
    private val modifierDefinitionCache: ModifierDefinitionCache by inject()

    /** Ревизию инвентаря двигает только [bumpInventory]: полная запись документа её не трогает. */
    override val managedFields: Set<String> = setOf("inventoryRevision")

    override val indexes = listOf(IndexSpec.unique("idx_unique_name", "name"), IndexSpec.on("userId"))

    /**
     * Игрок создаёт персонажа общим POST и мог прислать в теле что угодно: десятый уровень,
     * мешок золота, торговца со своими ценами. Поэтому от игрока берутся только имя, описание
     * и класс, всё остальное начинается с нуля. Администратор и сидинг (вызывающего у них нет)
     * пишут как есть.
     */
    override suspend fun admit(entity: Character): Character =
        if (caller()?.isAdmin != false) entity
        else Character(userId = entity.userId, name = entity.name.trim(), description = entity.description, classId = entity.classId)

    override suspend fun validateBeforeInsert(entity: Character, session: ClientSession) {
        val player = caller()?.takeUnless { it.isAdmin }
        if (player != null && entity.userId != player.user._id)
            throw AuthExceptions.funExceptionNotYourAccount("validateBeforeInsert", entity.userId)
        if (entity.name.isBlank()) throw CharacterExceptions.funExceptionName("validateBeforeInsert")
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
        // Первое активное и первое пассивное умение класса (0.69.0) - тоже сразу и в слотах
        if (entity.skills.learned.isEmpty()) entity.skills = features.logic.skills.SkillRules.starter(requireClass(entity).code)
    }

    override suspend fun validateAfterInsert(entity: Character, session: ClientSession) {
        val findedUser = userRepository.findByField(User::_id, entity.userId, session)
        if (findedUser == null) throw CharacterExceptions.funExceptionUserNotFound("validateAfterInsert", entity.userId)
        findedUser.countCharacters++
        if (findedUser.countCharacters > CONST_USER_MAX_CHARACTERS) throw CharacterExceptions.funExceptionMaxChars("validateAfterInsert")
        userRepository.update(findedUser, session)
        // Малая фляга жизни (0.69.0) - сразу на первом месте пояса
        equipmentCache.findByCode(features.logic.equipment.FlaskRules.STARTER)?.let { flask ->
            characterEquipmentRepository.insert(CharacterEquipment.fromEquipment(entity._id, flask).apply { equippedSlot = application.enums.EnumEquipmentType.FLASK }, session)
        }
    }

    override suspend fun validateAfterDelete(entity: Character, session: ClientSession) {
        characterEquipmentRepository.deleteByCharacter(entity._id, session)
        auctionLotRepository.deleteActiveBySeller(entity._id, session)
        // Место под персонажа освобождается, иначе после трёх удалений новый не создать
        userRepository.findByField(User::_id, entity.userId, session)?.let { owner ->
            owner.countCharacters = (owner.countCharacters - 1).coerceAtLeast(0)
            userRepository.update(owner, session)
        }
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
        requireExists(characterId, "getEquipmentsData")
        return characterEquipmentRepository.findByCharacter(characterId)
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
        val owned = character.bag[itemId] ?: 0L
        if (owned < amount) throw CharacterExceptions.funExceptionItemLowZero("spendItem", "$itemId:$owned")
        // Одна точечная запись вместо документа целиком (0.49.0). Фильтр по версии гарантирует, что
        // остаток в памяти и есть остаток в базе; версия растёт, как при любой записи, и объект в
        // памяти идёт в ногу с базой - следующая полная запись в той же транзакции не споткнётся.
        val field = "bag.$itemId"
        val now = LocalDateTime.now()
        val result = collection.updateOne(session,
            Filters.and(Filters.eq("_id", character._id), Filters.eq("version", character.version), Filters.gte(field, amount)),
            Updates.combine(
                if (owned == amount) Updates.unset(field) else Updates.inc(field, -amount),
                Updates.inc("version", 1L), Updates.set("updatedAt", now)))
        if (result.matchedCount == 0L) throw CharacterExceptions.funExceptionItemLowZero("spendItem", "$itemId:$owned")
        if (owned == amount) character.bag.remove(itemId) else character.bag[itemId] = owned - amount
        character.version += 1
        character.updatedAt = now
    }

    /**
     * Инвентарь героя изменился: ревизия растёт один раз на транзакцию (см. [config.beforeCommit]),
     * журнал запроса запоминает, откуда и куда, а лист статов в памяти забывается.
     */
    suspend fun bumpInventory(characterId: String, session: ClientSession) {
        val before = collection.withDocumentClass<Document>().findOneAndUpdate(session, Filters.eq("_id", characterId),
            Updates.inc("inventoryRevision", 1L),
            FindOneAndUpdateOptions().projection(Projections.include("inventoryRevision")).returnDocument(ReturnDocument.BEFORE))
        StatsMemo.forget(characterId)
        heroChanges()?.bumped(characterId, before?.getLong("inventoryRevision") ?: 0L)
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

                val total = (character.bag[itemId] ?: 0L) + amount
        if (total > CONST_ITEM_MAX_AMOUNT)
            throw CharacterExceptions.funExceptionItemOverAmount("earnItem", "$itemId:$total")
        character.bag[itemId] = total
        update(character, session)
    }

    /**
     * Итоговые характеристики персонажа: база класса на его уровне, дерево навыков и работающая
     * экипировка. Единственная точка расчёта - иначе потеряется чей-нибудь источник или будет
     * учтён предмет, который на самом деле не работает.
     */
    suspend fun calculateStats(characterId: String): CharacterStats =
        calculateStats(requireCharacter(characterId, "calculateStats"))

    /**
     * Лист уже прочитанного героя (с 0.49.0 - из памяти, пока версия персонажа, ревизия
     * инвентаря и справочники те же; тогда ни чтения, ни расчёта).
     * @param equipped надетое, если вызывающий его уже прочитал; иначе читается при промахе
     */
    suspend fun calculateStats(character: Character, equipped: List<CharacterEquipment>? = null): CharacterStats =
        StatsMemo.of(character._id, statsKey(character)) {
            CharacterStatsCalculator.calculate(
                character = character,
                characterClass = requireClass(character),
                skillNodes = character.skillNodes,
                equipped = equipped ?: characterEquipmentRepository.findEquipped(character._id)
            )
        }

    private fun statsKey(character: Character): String =
        "${character.version}:${character.inventoryRevision}:${characterClassCache.revision + equipmentCache.revision + modifierDefinitionCache.revision}"

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

    fun skillTreeState(character: Character): CharacterSkillTreeState = stateOf(character)

    /**
     * Берёт узел дерева.
     *
     * @throws SkillTreeExceptions.SkillTreeException если узел брать нельзя
     */
    suspend fun allocateSkillNode(characterId: String, nodeCode: String, choice: Int? = null): CharacterSkillTreeState {
        val character = requireCharacter(characterId, "allocateSkillNode")
        val node = requireNode(nodeCode, "allocateSkillNode")

        val available = stateOf(character).available

        SkillTreeAllocation.requireAllocatable(
            graph = skillTreeCache.graph(),
            node = node,
            taken = character.skillNodes.map { it.code },
            startNodeCode = requireClass(character).startNodeCode,
            available = available,
            choice = choice,
        )

        character.skillNodes.add(CharacterSkillNode.fromNode(node, choice))
        transactionExecute("allocateSkillNode $nodeCode") { session -> update(character, session) }

        return stateOf(character)
    }

    /**
     * Меняет вариант взятого атрибутного узла (с 0.63.0): узел остаётся взятым, очки и сферы
     * сожаления не тратятся - уходит одна сфера хаоса. Мастерство так не меняется: его вариант -
     * решение дороже, и оно по-прежнему только через возврат узла.
     *
     * @throws SkillTreeExceptions.SkillTreeException если узел не атрибутный, не взят, вариант тот же или нет сферы хаоса
     */
    suspend fun rechooseSkillNode(characterId: String, nodeCode: String, choice: Int): CharacterSkillTreeState {
        val method = "rechooseSkillNode"
        val character = requireCharacter(characterId, method)
        val node = requireNode(nodeCode, method)
        if (node.type == EnumSkillNodeType.MASTERY || node.options.isEmpty()) throw SkillTreeExceptions.funExceptionNotRechoosable(method, nodeCode)
        if (choice !in node.options.indices) throw SkillTreeExceptions.funExceptionChoice(method, "$nodeCode: $choice of ${node.options.size}")
        val index = character.skillNodes.indexOfFirst { it.code == nodeCode }
        if (index < 0) throw SkillTreeExceptions.funExceptionNotTaken(method, nodeCode)
        if (character.skillNodes[index].choice == choice) throw SkillTreeExceptions.funExceptionChoice(method, "$nodeCode: $choice is already chosen")

        val orbId = EnumCurrencyOrb.CHAOS_ORB.name.toStableObjectId()
        val owned = character.bag[orbId] ?: 0L
        if (owned < 1) throw SkillTreeExceptions.funExceptionNoChaos(method, nodeCode)
        if (owned == 1L) character.bag.remove(orbId) else character.bag[orbId] = owned - 1

        character.skillNodes[index] = CharacterSkillNode.fromNode(node, choice)
        transactionExecute("$method $nodeCode") { session -> update(character, session) }
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

        SkillTreeAllocation.requireRefundable(skillTreeCache.graph(), node, character.skillNodes.map { it.code })
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
        character.skillNodes.mapNotNull { skillTreeCache.findByCode(it.code) }
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
        characterEquipmentRepository.socketCodes(characterId)

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
                val owned = character.bag[orbId] ?: 0L
        if (owned < count) throw SkillTreeExceptions.funExceptionNoRegret(method, "$count, have $owned")
        if (owned == count.toLong()) character.bag.remove(orbId) else character.bag[orbId] = owned - count
    }

    /** Персонажи с пустой сумкой - фильтром в базе, а не чтением всех. */
    suspend fun withEmptyBag(session: ClientSession): List<Character> =
        collection.find(session, readFilter(Filters.and(
            Filters.ne(Character::starterGranted.name, true),
            Filters.or(Filters.exists("bag", false), Filters.eq("bag", Document())),
        ))).toList()

    /** Отмечает стартовый набор выданным всем, кто его ещё не получал; версия растёт вместе с документом. */
    suspend fun markStarterGranted(session: ClientSession): Long =
        collection.updateMany(
            session,
            Filters.ne(Character::starterGranted.name, true),
            Updates.combine(Updates.set(Character::starterGranted.name, true), Updates.inc(CONST_FIELD_VERSION, 1L)),
        ).modifiedCount

    /**
     * Выдаёт стартовый узел класса тем персонажам, у которых дерево пустое.
     *
     * Новым узел ставится при создании, этот шаг чинит созданных раньше и тех,
     * у кого узлы вычистила перебалансировка дерева.
     *
     * @return сколько персонажей получили стартовый узел
     */
    suspend fun ensureStartNodes(session: ClientSession): Int {
        val orphans = collection.find(session, readFilter(Filters.or(Filters.size("skillNodes", 0), Filters.exists("skillNodes", false)))).toList()

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

    /**
     * Убирает из сумок всех персонажей предметы, которых больше нет в `Items`.
     *
     * @param itemIds _id всех существующих предметов
     * @return сколько персонажей потеряли хотя бы один предмет
     */
    suspend fun pruneMissingBagItems(itemIds: Collection<String>, session: ClientSession): Long {
        if (itemIds.isEmpty()) return 0

        // Сумка - словарь по _id: фильтруем его пары конвейером, modifiedCount
        // посчитает только тех, у кого что-то действительно ушло
        val kept = Document("\$filter", Document("input", Document("\$objectToArray", "\$bag"))
            .append("cond", Document("\$in", listOf("\$\$this.k", itemIds.toList()))))
        val prune = Document("\$set", Document("bag", Document("\$arrayToObject", kept)))

        return collection.updateMany(session, Filters.exists("bag"), listOf(prune)).modifiedCount
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

    /** Персонаж по id или «не найден» от имени операции [method]. */
    suspend fun requireCharacter(characterId: String, method: String): Character =
        requireById(characterId) { CharacterExceptions.funExceptionNotFound(method, it) }

    /**
     * Владелец персонажа - одно поле по `_id`, без документа: доступ спрашивает его на каждой
     * команде, а персонаж со всей сумкой и деревом ему не нужен.
     */
    suspend fun ownerOf(characterId: String): String? =
        collection.withDocumentClass<Document>().find(readFilter(Filters.eq("_id", characterId)))
            .projection(Projections.include("userId")).limit(1).firstOrNull()?.getString("userId")

    /** То же без чтения документа: только проверка, что персонаж есть. */
    suspend fun requireExists(characterId: String, method: String) {
        if (!exists(characterId)) throw CharacterExceptions.funExceptionNotFound(method, characterId)
    }

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
        requireExists(characterId, "itemToInventory")
        val equipment = equipmentCache.findById(equipmentId)
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
        val character = requireCharacter(characterId, "addItem")

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
                var isChanged = false
        itemObj.forEach { itm ->
            if (itm.amount == 0L) return@forEach
            if (itm.amount > CONST_ITEM_MAX_AMOUNT) throw CharacterExceptions.funExceptionItemOverAmount(method, itm.toString())
            if (itm.amount < -CONST_ITEM_MAX_AMOUNT) throw CharacterExceptions.funExceptionItemOverAmount(method, itm.toString())
            if (itemsCache.findById(itm.itemId) == null) throw CharacterExceptions.funExceptionItemNotFound(method, itm.toString())
            // Награда сверх лимита стака сгорает: добыча не должна ни раздувать стак, ни срывать всю выдачу
            val total = ((character.bag[itm.itemId] ?: 0L) + itm.amount).coerceAtMost(maxOf(CONST_ITEM_MAX_AMOUNT, character.bag[itm.itemId] ?: 0L))
            if (total < 0) throw CharacterExceptions.funExceptionItemLowZero(method, itm.toString())
            //Зачем хранить id предмета без кол-ва
            if (total == 0L) character.bag.remove(itm.itemId) else character.bag[itm.itemId] = total
            isChanged = true
        }
        return isChanged
    }
}

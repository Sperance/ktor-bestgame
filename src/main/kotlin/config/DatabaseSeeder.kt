package config

import application.enums.EnumSkillNodeType
import CONST_SEED_ORBS_AMOUNT
import SEED_ADMIN_PASSWORD
import SEED_TEST_PLAYER_PASSWORD
import application.enums.EnumCurrencyOrb
import application.enums.EnumRarity
import application.enums.EnumUserRoles
import com.mongodb.kotlin.client.coroutine.ClientSession
import config.MongoFactory.transactionExecute
import extensions.printLog
import features.data.character.Character
import features.data.character.character_data.CharacterItems
import features.data.character.CharacterRepository
import features.data.equipment.EquipmentRepository
import features.data.items.ItemsRepository
import features.caches.BlockListCache
import features.caches.CharacterClassCache
import features.caches.EquipmentCache
import features.caches.ExperienceLevelCache
import features.caches.ItemsCache
import features.caches.ModifierDefinitionCache
import features.caches.PoolCache
import features.caches.SkillTreeCache
import features.data.auction.AuctionLotRepository
import features.data.blockList.BlockListRepository
import features.data.inventory.CharacterEquipmentRepository
import features.data.redemptionCodes.RedemptionCodesRepository
import features.data.user.User
import features.data.user.UserRepository
import features.data.auth.AuthSessionRepository
import features.logic.modifiers.ModifierDefinition
import features.logic.modifiers.ModifierDefinitionRepository
import features.logic.pools.PoolRepository
import features.logic.progression.CharacterClassRepository
import features.logic.progression.ExperienceLevelRepository
import features.logic.skilltree.SkillTreeNodeRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Заполнение БД начальными данными при старте сервера.
 *
 * Сначала разовая очистка базы ([DatabaseWipe], 0.56.0, 0.66.3, 0.67.0 и 0.69.0), потом справочники и сиды. Каждый блок
 * проверяет, есть ли уже данные — повторный запуск безопасен.
 */
object DatabaseSeeder : KoinComponent {

    private val userRepository: UserRepository by inject()
    private val authSessionRepository: AuthSessionRepository by inject()
    private val characterRepository: CharacterRepository by inject()
    private val itemsRepository: ItemsRepository by inject()
    private val equipmentRepository: EquipmentRepository by inject()
    private val blockListRepository: BlockListRepository by inject()
    private val redemptionCodesRepository: RedemptionCodesRepository by inject()
    private val characterEquipmentRepository: CharacterEquipmentRepository by inject()
    private val auctionLotRepository: AuctionLotRepository by inject()
    private val modifierDefinitionRepository: ModifierDefinitionRepository by inject()
    private val poolRepository: PoolRepository by inject()
    private val equipmentCache: EquipmentCache by inject()
    private val modifierDefinitionCache: ModifierDefinitionCache by inject()
    private val poolCache: PoolCache by inject()
    private val itemsCache: ItemsCache by inject()
    private val skillTreeCache: SkillTreeCache by inject()
    private val skillTreeNodeRepository: SkillTreeNodeRepository by inject()
    private val characterClassRepository: CharacterClassRepository by inject()
    private val experienceLevelRepository: ExperienceLevelRepository by inject()
    private val characterClassCache: CharacterClassCache by inject()
    private val experienceLevelCache: ExperienceLevelCache by inject()
    private val blockListCache: BlockListCache by inject()

    suspend fun seed() {

        try {
            getKoin()
        } catch (e: Exception) {
            printLog("❌ Koin not initialized! Call startKoin first.")
            return
        }

        printLog("Database seeding started")

        DatabaseWipe.runOnce()
        ensureIndexes()

        transactionExecute { session ->
            // Справочники первыми: на них ссылается всё остальное, а тяги кешей строятся по пулам
            seedPools(session)
            val definitions = seedModifiers(session)
            seedProgression(session, definitions)
            seedEquipment(session, definitions)
            seedSkillTree(session, definitions)
            pruneModifiers(session, definitions)

            seedUsers(session)
            seedCharacters(session)
            seedStartNodes(session)
            seedItems(session)
            seedCurrency(session)
            pruneItems(session)
            seedRedemptionCodes(session)
            seedEqipmentCharacters(session)
            seedCurrencyToCharacters(session)
            characterRepository.markStarterGranted(session)
        }

        initializeCaches()
        // Волшебный или редкий предмет без аффиксов - брак старых роллов (0.65.0): чинится сразу.
        transactionExecute { session -> characterEquipmentRepository.repairAffixes(session) }
            .takeIf { it > 0 }?.let { printLog("  → affixes repaired on $it items") }

        printLog("Database seeding completed")
    }

    /**
     * Индексы всех коллекций - до старта транзакции: создание индекса меняет каталог MongoDB,
     * и открытая транзакция упала бы с WriteConflict "due to catalog changes".
     */
    private suspend fun ensureIndexes() = coroutineScope {
        listOf(
            userRepository, authSessionRepository, characterRepository, characterEquipmentRepository,
            auctionLotRepository, itemsRepository, equipmentRepository, blockListRepository,
            redemptionCodesRepository, modifierDefinitionRepository, poolRepository,
            skillTreeNodeRepository, characterClassRepository, experienceLevelRepository,
        ).map { async { it.ensureIndexes() } }.awaitAll()
        printLog("  → indexes ensured")
    }

    /**
     * Загрузка кэшей после сидинга.
     *
     * Кэши намеренно не грузятся при старте Koin: они поднимались бы раньше
     * сидера и падали на документах старого формата - то есть раньше, чем
     * пересев успевал бы их починить.
     */
    private suspend fun initializeCaches() = coroutineScope {
        listOf(
            poolCache, modifierDefinitionCache, characterClassCache, experienceLevelCache,
            equipmentCache, skillTreeCache, itemsCache, blockListCache
        ).map { async { it.initializeCache() } }.awaitAll()

        printLog("  → caches loaded")
    }

    /** Модификаторы, которых больше нет в игре, снимаются с предметов - в инвентаре и на аукционе. */
    private suspend fun pruneModifiers(session: ClientSession, definitions: List<ModifierDefinition>) {
        val codes = definitions.map { it.code }
        val items = characterEquipmentRepository.pruneMissingModifiers(codes, session)
        val lots = auctionLotRepository.pruneMissingModifiers(codes, session)
        if (items + lots > 0) printLog("  → removed modifiers stripped from $items items and $lots lots")
    }

    // ==================== Progression ====================

    /**
     * Классы персонажей и таблица уровней. Пересевается на каждом старте,
     * _id стабильны - персонажи ссылаются на класс, а не хранят его снимок.
     */
    private suspend fun seedProgression(session: ClientSession, definitions: List<ModifierDefinition>) {
        printLog("Seeding progression...")

        characterClassRepository.deleteAll(session)
        experienceLevelRepository.deleteAll(session)

        val classes = characterClassRepository.insertMany(ProgressionSeeder.seedClasses(definitions), session)
        val levels = experienceLevelRepository.insertMany(ProgressionSeeder.seedLevels(), session)

        characterClassCache.initializeCache(session)
        experienceLevelCache.initializeCache(session)

        printLog("  → ${classes.size} classes, ${levels.size} levels created")
    }

    // ==================== Users ====================

    private suspend fun seedUsers(session: ClientSession) {
        // Считаем и мягко удалённых: их логины и почты остались занятыми,
        // пересев упёрся бы в уникальный индекс
        if (userRepository.count(includeDeleted = true) > 0) return

        printLog("Seeding users...")

        // Паролей в коде больше нет: администратор и тестовый игрок заводятся, только если
        // пароль задан в окружении. Сервер, поднятый без настроек, не создаёт аккаунт с
        // паролем из открытого репозитория.
        val listItems = arrayListOf<User>()
        SEED_ADMIN_PASSWORD?.let {
            listItems.add(User(name = "Admin", email = "admin@game.com", age = 25, login = "admin",
                password = it, role = EnumUserRoles.ADMIN))
        }
        SEED_TEST_PLAYER_PASSWORD?.let {
            listItems.add(User(name = "TestPlayer", email = "player@game.com", age = 22, login = "test1", password = it))
        }
        if (listItems.isEmpty()) {
            printLog("  → ADMIN_PASSWORD and TEST_PLAYER_PASSWORD are not set, no users seeded")
            return
        }

        userRepository.insertMany(listItems, session)

        printLog("  → ${listItems.size} users created")
    }

    // ==================== Modifiers ====================

    /**
     * Пулы (с 0.56.0) - одна коллекция на все: модификаторы предметов и монстров, базы, уникалки
     * и мифические предметы. Пересеваются на каждом старте, как и остальные справочники.
     */
    private suspend fun seedPools(session: ClientSession) {
        printLog("Seeding pools...")

        poolRepository.deleteAll(session)
        val pools = poolRepository.insertMany(PoolSeeder.pools, session)
        poolCache.initializeCache(session)

        printLog("  → ${pools.size} pools created")
    }

    /**
     * Описания модификаторов вместе с их тирами (с 0.56.0 тиры внутри описания).
     *
     * Справочник пересобирается на каждом старте, чтобы правки в таблицах модификаторов сразу
     * попадали в базу. Предметы ссылаются на описание кодом, поэтому переживают пересев.
     *
     * @return актуальные описания модификаторов
     */
    private suspend fun seedModifiers(session: ClientSession): List<ModifierDefinition> {
        printLog("Seeding modifiers...")

        modifierDefinitionRepository.deleteAll(session)

        val definitions = modifierDefinitionRepository.insertMany(
            ModifierSeeder.seedDefinitions() + UniqueEquipmentSeeder.seedDefinitions(),
            session
        )

        modifierDefinitionCache.initializeCache(session)

        printLog("  → ${definitions.size} modifier definitions created")

        return definitions
    }

    /**
     * Шаблоны экипировки пересобираются на каждом старте, чтобы правки
     * в EquipmentSeeder сразу попадали в базу.
     *
     * Чистим именно deleteAll(session), а не drop: drop меняет каталог и рвёт
     * открытую транзакцию. _id шаблонов стабильны, поэтому ссылки из инвентаря
     * переживают пересев.
     */
    private suspend fun seedEquipment(session: ClientSession, definitions: List<ModifierDefinition>) {
        printLog("Seeding equipment...")

        equipmentRepository.deleteAll(session)

        val listItems = EquipmentSeeder(definitions).seed()

        equipmentRepository.insertMany(listItems, session)
        equipmentCache.initializeCache(session)

        printLog("  → ${listItems.size} equipments created")
    }

    // ==================== Characters ====================

    private suspend fun seedCharacters(session: ClientSession) {
        // Считаем и мягко удалённых: их имена остались занятыми в уникальном индексе
        if (characterRepository.count(includeDeleted = true) > 0) return

        printLog("Seeding characters...")
        val userRepoAll = userRepository.findAll(session)
        if (userRepoAll.isEmpty()) {
            printLog("  → no users, characters seeding skipped")
            return
        }

        val marauder = characterClassCache.findByCode("MARAUDER")
        val witch = characterClassCache.findByCode("WITCH")
        if (marauder == null || witch == null) {
            printLog("  → no character classes, characters seeding skipped")
            return
        }

        val listItems = arrayListOf<Character>()
        listItems.add(
            Character(
                name = "Warrior",
                description = "STRONG pipster",
                userId = userRepoAll.first()._id,
                classId = marauder._id,
                level = 10,
                experience = 61693.0,
            )
        )
        listItems.add(
            Character(
                name = "Mage",
                description = "Мудрый pipster",
                userId = userRepoAll.last()._id,
                classId = witch._id,
                level = 10,
                experience = 61693.0,
            )
        )

        characterRepository.insertMany(listItems, session)

        printLog("  → ${listItems.size} characters created")
    }

    /**
     * Стартовые узлы дерева персонажам, созданным до автовыдачи.
     *
     * Новым персонажам узел выдаётся прямо в транзакции создания,
     * а этот шаг чинит уже существующих.
     */
    private suspend fun seedStartNodes(session: ClientSession) {
        val created = characterRepository.ensureStartNodes(session)
        if (created > 0) printLog("  → $created characters got their class start node")
    }

    // ==================== Items ====================

    private suspend fun seedItems(session: ClientSession) {
        if (itemsRepository.count() > 0) return

        printLog("Seeding items...")
        val listItems = ItemsSeeder.seed()

        itemsRepository.insertMany(listItems, session)

        printLog("  → ${listItems.size} items created")
    }

    /** Предметы, которых больше нет в контенте, удаляются из `Items` и из сумок персонажей. */
    private suspend fun pruneItems(session: ClientSession) {
        val seeded = ItemsSeeder.seed().map { it._id }
        val removed = itemsRepository.deleteMissing(seeded, EnumCurrencyOrb.CATEGORY, session)
        if (removed > 0) itemsCache.initializeCache(session)

        val touched = characterRepository.pruneMissingBagItems(itemsRepository.findAll(session).map { it._id }, session)
        if (removed + touched > 0) printLog("  → $removed removed items dropped, bags of $touched characters cleaned")
    }

    // ==================== Currency ====================

    /**
     * Валютные сферы в коллекции `Items`.
     *
     * Категория пересевается на каждом старте, остальные предметы не трогаем.
     * _id сфер стабильны, поэтому запасы персонажей переживают пересев.
     */
    private suspend fun seedCurrency(session: ClientSession) {
        printLog("Seeding currency...")

        itemsRepository.deleteByCategory(EnumCurrencyOrb.CATEGORY, session)

        val listItems = CurrencySeeder.seed()
        itemsRepository.insertMany(listItems, session)
        itemsCache.initializeCache(session)

        printLog("  → ${listItems.size} currency orbs created")
    }

    /**
     * Стартовый запас сфер персонажам, у которых ещё нет простых предметов и которые его не получали.
     */
    private suspend fun seedCurrencyToCharacters(session: ClientSession) {
        val orbs = itemsCache.findByCategory(EnumCurrencyOrb.CATEGORY)
        if (orbs.isEmpty()) return

        val characters = characterRepository.withEmptyBag(session)
        if (characters.isEmpty()) return

        printLog("Seeding starting currency...")

        characters.forEach { character ->
            character.bag = orbs.associateTo(mutableMapOf()) { it._id to CONST_SEED_ORBS_AMOUNT }
            characterRepository.update(character, session)
        }

        printLog("  → ${characters.size} characters got $CONST_SEED_ORBS_AMOUNT of each orb")
    }

    private suspend fun seedRedemptionCodes(session: ClientSession) {
        if (redemptionCodesRepository.count() > 0) return

        printLog("Seeding RedemptionCodes...")

        val listItems = RedemptionSeeder.seed()
        redemptionCodesRepository.insertMany(listItems, session)

        printLog("  → ${listItems.size} RedemptionCodes created")
    }

    // ==================== Skill tree ====================

    /**
     * Дерево навыков. Пересевается на каждом старте, _id узлов стабильны.
     *
     * Персонажи хранят только коды взятых узлов, поэтому перебалансировка
     * доезжает до всех сразу - как в POE, где значения пассивок не бывают
     * легаси. Чистятся лишь ссылки на узлы, которых в дереве не осталось.
     */
    private suspend fun seedSkillTree(session: ClientSession, definitions: List<ModifierDefinition>) {
        printLog("Seeding skill tree...")

        skillTreeNodeRepository.deleteAll(session)

        val listItems = SkillTreeSeeder.seed(definitions)
        skillTreeNodeRepository.insertMany(listItems, session)
        skillTreeCache.initializeCache(session)

        val touched = characterRepository.pruneMissingSkillNodes(listItems.map { it.code }, session)
        if (touched > 0) printLog("  → $touched characters lost nodes that are no longer in the tree")
        val released = characterEquipmentRepository.releaseMissingSockets(listItems.filter { it.type == EnumSkillNodeType.JEWEL_SOCKET }.map { it.code }, session)
        if (released > 0) printLog("  → $released jewels returned to the bag from sockets that are no longer in the tree")

        printLog("  → ${listItems.size} skill tree nodes created")
    }

    // ==================== Inventory ====================

    /**
     * Стартовая экипировка персонажей.
     *
     * Каждый предмет инвентаря - отдельный документ коллекции `CharacterEquipment`
     * со своими зароленными модификаторами.
     */
    private suspend fun seedEqipmentCharacters(session: ClientSession) {
        printLog("Seeding character inventory...")

        val characters = characterRepository.findAll(session)
        val equipments = equipmentRepository.findAll(session)
        val equipmentIds = equipments.map { it._id }.toSet()
        val startRarities = listOf(EnumRarity.COMMON, EnumRarity.UNIQUE)

        if (equipmentIds.isEmpty()) {
            printLog("  → no equipment templates, inventory seeding skipped")
            return
        }

        // Инвентарь, ссылающийся на уже несуществующий шаблон, чистим
        val dropped = characterEquipmentRepository.deleteByMissingEquipment(equipmentIds, session)
        if (dropped > 0) printLog("  → $dropped outdated character equipments removed")

        // Кто уже с вещами - одним запросом, а не подсчётом на каждого персонажа
        val equipped = characterEquipmentRepository.owners(session)
        val byRarity = equipments.groupBy { it.rarity }
        var created = 0
        characters.filterNot { it.starterGranted || it._id in equipped }.forEach { char ->
            val starters = startRarities.mapNotNull { byRarity[it]?.randomOrNull() }
            created += characterEquipmentRepository.addAllFromEquipment(char._id, starters, session).size
        }

        printLog("  → $created character equipments created")
    }
}
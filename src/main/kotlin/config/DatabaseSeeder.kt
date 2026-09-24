package config

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
import features.data.character.character_data.toStorage
import features.data.character.CharacterRepository
import features.data.equipment.EquipmentRepository
import features.data.items.ItemsRepository
import features.caches.BlockListCache
import features.caches.CharacterClassCache
import features.caches.EquipmentCache
import features.caches.ExperienceLevelCache
import features.caches.ItemsCache
import features.caches.ModifierDefinitionCache
import features.caches.ModifierTierCache
import features.caches.RecipeCache
import features.caches.SkillTreeCache
import features.data.auction.AuctionLotRepository
import features.data.blockList.BlockListRepository
import features.data.inventory.CharacterEquipmentRepository
import features.data.recipe.RecipeRepository
import features.data.redemptionCodes.RedemptionCodesRepository
import features.data.user.User
import features.data.user.UserRepository
import features.logic.modifiers.ModifierDefinition
import features.logic.modifiers.ModifierDefinitionRepository
import features.logic.modifiers.ModifierTierRepository
import features.logic.progression.CharacterClassRepository
import features.logic.progression.ExperienceLevelRepository
import features.logic.skilltree.SkillTreeNodeRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Заполнение БД начальными данными при старте сервера.
 *
 * Каждый блок проверяет, есть ли уже данные — повторный запуск безопасен.
 */
object DatabaseSeeder : KoinComponent {

    private val userRepository: UserRepository by inject()
    private val characterRepository: CharacterRepository by inject()
    private val itemsRepository: ItemsRepository by inject()
    private val equipmentRepository: EquipmentRepository by inject()
    private val blockListRepository: BlockListRepository by inject()
    private val recipeRepository: RecipeRepository by inject()
    private val redemptionCodesRepository: RedemptionCodesRepository by inject()
    private val characterEquipmentRepository: CharacterEquipmentRepository by inject()
    private val auctionLotRepository: AuctionLotRepository by inject()
    private val modifierDefinitionRepository: ModifierDefinitionRepository by inject()
    private val modifierTierRepository: ModifierTierRepository by inject()
    private val equipmentCache: EquipmentCache by inject()
    private val modifierDefinitionCache: ModifierDefinitionCache by inject()
    private val modifierTierCache: ModifierTierCache by inject()
    private val itemsCache: ItemsCache by inject()
    private val skillTreeCache: SkillTreeCache by inject()
    private val skillTreeNodeRepository: SkillTreeNodeRepository by inject()
    private val characterClassRepository: CharacterClassRepository by inject()
    private val experienceLevelRepository: ExperienceLevelRepository by inject()
    private val characterClassCache: CharacterClassCache by inject()
    private val experienceLevelCache: ExperienceLevelCache by inject()
    private val blockListCache: BlockListCache by inject()
    private val recipeCache: RecipeCache by inject()

    suspend fun seed() {

        try {
            getKoin()
        } catch (e: Exception) {
            printLog("❌ Koin not initialized! Call startKoin first.")
            return
        }

        printLog("Database seeding started")

        initializeRepositories()

        transactionExecute { session ->
            // Справочники первыми: на них ссылается всё остальное
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
            seedRedemptionCodes(session)
            seedEqipmentCharacters(session)
            seedCurrencyToCharacters(session)
        }

        initializeCaches()

        printLog("Database seeding completed")
    }

    /**
     * Прогрев репозиториев до старта транзакции.
     *
     * Репозиторий создаёт свои индексы при первом обращении, а создание индекса -
     * это изменение каталога MongoDB. Если оно случится при уже открытой транзакции,
     * та упадёт с WriteConflict "due to catalog changes".
     */
    private fun initializeRepositories() {
        val repositories = listOf(
            userRepository,
            characterRepository,
            characterEquipmentRepository,
            auctionLotRepository,
            itemsRepository,
            equipmentRepository,
            blockListRepository,
            recipeRepository,
            redemptionCodesRepository,
            modifierDefinitionRepository,
            modifierTierRepository,
            skillTreeNodeRepository,
            characterClassRepository,
            experienceLevelRepository
        )
        printLog("  → ${repositories.size} repositories initialized")
    }

    /**
     * Загрузка кэшей после сидинга.
     *
     * Кэши намеренно не грузятся при старте Koin: они поднимались бы раньше
     * сидера и падали на документах старого формата - то есть раньше, чем
     * пересев успевал бы их починить.
     */
    private suspend fun initializeCaches() {
        listOf(
            modifierDefinitionCache, modifierTierCache, characterClassCache, experienceLevelCache,
            equipmentCache, skillTreeCache, itemsCache, recipeCache, blockListCache
        ).forEach { it.initializeCache() }

        printLog("  → caches loaded")
    }

    /** Модификаторы, которых больше нет в игре, снимаются с предметов - в инвентаре и на аукционе. */
    private suspend fun pruneModifiers(session: ClientSession, definitions: List<ModifierDefinition>) {
        val ids = definitions.map { it._id }
        val items = characterEquipmentRepository.pruneMissingModifiers(ids, session)
        val lots = auctionLotRepository.pruneMissingModifiers(ids, session)
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
     * Описания модификаторов и их тиры - две отдельные коллекции Mongo.
     *
     * Справочник пересобирается на каждом старте, чтобы правки в таблицах
     * модификаторов сразу попадали в базу. _id выводятся из кода модификатора
     * и стабильны, поэтому зароленные модификаторы предметов переживают пересев.
     *
     * @return актуальные описания модификаторов
     */
    private suspend fun seedModifiers(session: ClientSession): List<ModifierDefinition> {
        printLog("Seeding modifiers...")

        modifierTierRepository.deleteAll(session)
        modifierDefinitionRepository.deleteAll(session)

        val definitions = modifierDefinitionRepository.insertMany(
            ModifierSeeder.seedDefinitions() + UniqueEquipmentSeeder.seedDefinitions(),
            session
        )
        val tiers = modifierTierRepository.insertMany(
            ModifierSeeder.seedTiers(definitions) + UniqueEquipmentSeeder.seedTiers(definitions),
            session
        )

        modifierDefinitionCache.initializeCache(session)
        modifierTierCache.initializeCache(session)

        printLog("  → ${definitions.size} modifier definitions created")
        printLog("  → ${tiers.size} modifier tiers created")

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
     * Стартовый запас сфер персонажам, у которых ещё нет простых предметов.
     */
    private suspend fun seedCurrencyToCharacters(session: ClientSession) {
        val orbs = itemsCache.getCache().filter { it.category == EnumCurrencyOrb.CATEGORY }
        if (orbs.isEmpty()) return

        val characters = characterRepository.findAll(session).filter { it.items.isEmpty() }
        if (characters.isEmpty()) return

        printLog("Seeding starting currency...")

        characters.forEach { character ->
            character.items = orbs.map { CharacterItems(it._id, CONST_SEED_ORBS_AMOUNT) }.toStorage()
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

        val legacy = characterEquipmentRepository.deleteLegacyParams(session)
        if (legacy > 0) printLog("  → $legacy character equipments with legacy modifiers removed")

        // Редкость переехала на экземпляр предмета, у старых документов её нет
        var backfilled = 0L
        equipments.groupBy { it.rarity }.forEach { (rarity, templates) ->
            backfilled += characterEquipmentRepository.backfillRarity(templates.map { it._id }, rarity, session)
        }
        if (backfilled > 0) printLog("  → $backfilled character equipments got their rarity")

        var created = 0
        characters.forEach { char ->
            if (characterEquipmentRepository.countByCharacter(char._id, session) > 0) return@forEach

            startRarities.forEach { rarity ->
                val template = equipments.filter { it.rarity == rarity }.randomOrNull() ?: return@forEach
                characterEquipmentRepository.addFromEquipment(char._id, template, session)
                created++
            }
        }

        printLog("  → $created character equipments created")
    }
}
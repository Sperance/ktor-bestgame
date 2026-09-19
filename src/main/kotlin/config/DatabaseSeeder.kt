package config

import CONST_SEED_ORBS_AMOUNT
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
import features.data.items.Items
import features.data.items.ItemsRepository
import features.caches.EquipmentCache
import features.caches.ItemsCache
import features.caches.ModifierDefinitionCache
import features.caches.ModifierTierCache
import features.data.blockList.BlockListRepository
import features.data.inventory.CharacterEquipmentRepository
import features.data.recipe.RecipeRepository
import features.data.redemptionCodes.RedemptionCodes
import features.data.redemptionCodes.RedemptionCodesRepository
import features.data.user.User
import features.data.user.UserRepository
import features.logic.modifiers.ModifierDefinition
import features.logic.modifiers.ModifierDefinitionRepository
import features.logic.modifiers.ModifierTierRepository
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
    private val modifierDefinitionRepository: ModifierDefinitionRepository by inject()
    private val modifierTierRepository: ModifierTierRepository by inject()
    private val equipmentCache: EquipmentCache by inject()
    private val modifierDefinitionCache: ModifierDefinitionCache by inject()
    private val modifierTierCache: ModifierTierCache by inject()
    private val itemsCache: ItemsCache by inject()

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
            seedUsers(session)
            seedCharacters(session)
            seedItems(session)
            val definitions = seedModifiers(session)
            seedEquipment(session, definitions)
            seedCurrency(session)
            seedRedemptionCodes(session)
            seedEqipmentCharacters(session)
            seedCurrencyToCharacters(session)
        }

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
            itemsRepository,
            equipmentRepository,
            blockListRepository,
            recipeRepository,
            redemptionCodesRepository,
            modifierDefinitionRepository,
            modifierTierRepository
        )
        printLog("  → ${repositories.size} repositories initialized")
    }

    // ==================== Users ====================

    private suspend fun seedUsers(session: ClientSession) {
        if (userRepository.count() > 0) return

        printLog("Seeding users...")

        val listItems = arrayListOf<User>()
        listItems.add(
            User(
                name = "Admin",
                email = "admin@game.com",
                age = 25,
                login = "admin",
                password = "P32543254",
                role = EnumUserRoles.ADMIN
            )
        )
        listItems.add(
            User(
                name = "TestPlayer",
                email = "player@game.com",
                age = 22,
                password = "P123456",
                login = "test1"
            )
        )

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
        if (characterRepository.count() > 0) return

        printLog("Seeding characters...")
        val userRepoAll = userRepository.findAll(session)

        val listItems = arrayListOf<Character>()
        listItems.add(
            Character(
                name = "Warrior",
                description = "STRONG pipster",
                userId = userRepoAll.first()._id,
            )
        )
        listItems.add(
            Character(
                name = "Mage",
                description = "Мудрый pipster",
                userId = userRepoAll.last()._id,
                level = 5,
                experience = 1200.0,
            )
        )

        characterRepository.insertMany(listItems, session)

        printLog("  → ${listItems.size} characters created")
    }

    // ==================== Items ====================

    private suspend fun seedItems(session: ClientSession) {
        if (itemsRepository.count() > 0) return

        printLog("Seeding items...")
        val listItems = ArrayList<Items>()
        listItems.add(
            Items(
                category = "WOOD_STOCK",
                subCategory =  "LOG",
                description = "Кусок дерева (полено)",
                price = 10,
                name = "Дрееово жыжы"
            )
        )
        listItems.add(
            Items(
                category = "STONE_STOCK",
                subCategory = "STONE",
                description = "Кучка кала",
                price = 12,
                name = "Кал"
            )
        )
        listItems.add(
            Items(
                category = "STONE_STOCK",
                subCategory = "STONE",
                description = "Кучка кала 2",
                price = 22,
                name = "Кал23"
            )
        )
        listItems.add(
            Items(
                category = "CONSUMABLE",
                subCategory = "HEALTH",
                description = "Восстанавливает здоровье",
                price = 80,
                name = "Зелье"
            )
        )

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

        val listItems = arrayListOf<RedemptionCodes>()
        listItems.add(
            RedemptionCodes("ALFA_BETA_GAMMA", listOf(), "")
        )

        redemptionCodesRepository.insertMany(listItems, session)

        printLog("  → ${listItems.size} RedemptionCodes created")
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
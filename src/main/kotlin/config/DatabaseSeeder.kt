package config

import application.enums.EnumRarity
import application.enums.EnumUserRoles
import com.mongodb.kotlin.client.coroutine.ClientSession
import config.MongoFactory.transactionExecute
import extensions.printLog
import features.data.character.Character
import features.data.character.CharacterRepository
import features.data.equipment.EquipmentRepository
import features.data.items.Items
import features.data.items.ItemsRepository
import features.caches.EquipmentCache
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
            seedRedemptionCodes(session)
            seedEqipmentCharacters(session)
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
     * @return актуальные описания модификаторов (существующие или только что созданные)
     */
    private suspend fun seedModifiers(session: ClientSession): List<ModifierDefinition> {
        if (modifierDefinitionRepository.count(session) > 0) {
            return modifierDefinitionRepository.findAll(session)
        }

        printLog("Seeding modifiers...")

        val definitions = modifierDefinitionRepository.insertMany(ModifierSeeder.seedDefinitions(), session)
        val tiers = modifierTierRepository.insertMany(ModifierSeeder.seedTiers(definitions), session)

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
        val startRarities = listOf(EnumRarity.COMMON, EnumRarity.LEGENDARY)

        if (equipmentIds.isEmpty()) {
            printLog("  → no equipment templates, inventory seeding skipped")
            return
        }

        // Шаблоны пересоздаются на каждом старте, поэтому инвентарь,
        // ссылающийся на уже несуществующий шаблон, чистим.
        val dropped = characterEquipmentRepository.deleteByMissingEquipment(equipmentIds, session)
        if (dropped > 0) printLog("  → $dropped outdated character equipments removed")

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
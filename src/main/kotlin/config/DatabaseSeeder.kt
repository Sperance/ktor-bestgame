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
import features.data.blockList.BlockListRepository
import features.data.character.character_data.CharacterEquipments
import features.data.recipe.RecipeRepository
import features.data.redemptionCodes.RedemptionCodes
import features.data.redemptionCodes.RedemptionCodesRepository
import features.data.user.User
import features.data.user.UserRepository
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

    suspend fun seed() {

        try {
            getKoin()
        } catch (e: Exception) {
            printLog("❌ Koin not initialized! Call startKoin first.")
            return
        }

        printLog("Database seeding started")

        transactionExecute { session ->
            seedUsers(session)
            seedCharacters(session)
            seedItems(session)
            seedEquipment(session)
            seedRedemptionCodes(session)
            seedEqipmentCharacters(session)
        }

        printLog("Database seeding completed")
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

    private suspend fun seedEquipment(session: ClientSession) {
        equipmentRepository.deleteAll()
        if (equipmentRepository.count() > 0) return

        printLog("Seeding equipment...")

        val listItems = EquipmentSeeder.seed()

        equipmentRepository.insertMany(listItems, session)

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

    private suspend fun seedEqipmentCharacters(session: ClientSession) {
        val characters = characterRepository.findAll(session)
        val equipments = equipmentRepository.findAll(session)

        characters.forEach { char ->
            char.equipments.add(CharacterEquipments.fromEquipment(equipments.filter { it.rarity == EnumRarity.COMMON }.random()))
            char.equipments.add(CharacterEquipments.fromEquipment(equipments.filter { it.rarity == EnumRarity.LEGENDARY }.random()))
        }

        characterRepository.bulkUpdate(characters, session)
    }
}
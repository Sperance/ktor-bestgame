package config

import application.enums.EnumEquipmentType
import application.enums.EnumStatHelper
import application.enums.EnumUserRoles
import config.MongoFactory.transactionExecute
import extensions.printLog
import features.data.character.Character
import features.data.character.CharacterEquipments
import features.data.character.CharacterRepository
import features.data.equipment.Equipment
import features.data.equipment.EquipmentRepository
import features.data.items.Items
import features.data.items.ItemsRepository
import features.data.property.Property
import features.data.property.PropertyRepository
import features.data.user.User
import features.data.user.UserRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue

/**
 * Заполнение БД начальными данными при старте сервера.
 *
 * Каждый блок проверяет, есть ли уже данные — повторный запуск безопасен.
 */
object DatabaseSeeder : KoinComponent {

    private val userRepository: UserRepository by inject()
    private val characterRepository: CharacterRepository by inject()
    private val itemsRepository: ItemsRepository by inject()
    private val propertyRepository: PropertyRepository by inject()
    private val equipmentRepository: EquipmentRepository by inject()

    suspend fun seed() {

        try {
            getKoin()
        } catch (e: Exception) {
            printLog("❌ Koin not initialized! Call startKoin first.")
            return
        }

        printLog("Database seeding started")

        seedUsers()
        seedCharacters()
        seedEquipment()
        seedItems()
        seedProperty()

        printLog("Database seeding completed")
    }

    // ==================== Users ====================

    private suspend fun seedUsers() {
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

        transactionExecute { session ->
            userRepository.insertMany(listItems, session)
        }

        printLog("  → ${listItems.size} users created")
    }


    private suspend fun seedEquipment() {
        if (equipmentRepository.count() > 0) return

        printLog("Seeding equipment...")

        val listItems = arrayListOf<Equipment>()
        listItems.add(
            Equipment(
                slot = EnumEquipmentType.BODY,
                name = "Body of THORN",
                itemLevel = 10
            )
        )
        listItems.add(
            Equipment(
                slot = EnumEquipmentType.RING,
                name = "Ring of THORN",
                itemLevel = 4
            )
        )
        listItems.add(
            Equipment(
                slot = EnumEquipmentType.RING,
                name = "Ring of PUSSY",
                itemLevel = 6
            )
        )

        transactionExecute { session ->
            equipmentRepository.insertMany(listItems, session)
        }

        printLog("  → ${listItems.size} equipments created")
    }

    // ==================== Characters ====================

    private suspend fun seedCharacters() {
        if (characterRepository.count() > 0) return

        printLog("Seeding characters...")
        val userRepoAll = userRepository.findAll()

        val listItems = arrayListOf<Character>()
        listItems.add(
            Character(
                name = "Warrior",
                description = "STRONG pipster",
                userId = userRepoAll.first().getId(),
            )
        )
        listItems.add(
            Character(
                name = "Mage",
                description = "Мудрый pipster",
                userId = userRepoAll.last().getId(),
                level = 5,
                experience = 1200,
            )
        )

        transactionExecute { session ->
            characterRepository.insertMany(listItems, session)
        }

        printLog("  → ${listItems.size} characters created")
    }

    // ==================== Items ====================

    private suspend fun seedItems() {
        if (itemsRepository.count() > 0) return

        printLog("Seeding items...")
        val listItems = ArrayList<Items>()
        listItems.add(
            Items(
                name = "Дерево",
                description = "Кусок дерева (полено)",
                price = 10
            )
        )
        listItems.add(
            Items(
                name = "Камень",
                description = "Кучка кала",
                price = 12
            )
        )
        listItems.add(
            Items(
                name = "Зелье здоровья",
                description = "Восстанавливает здоровье",
                price = 80
            )
        )

        transactionExecute { session ->
            itemsRepository.insertMany(listItems, session)
        }

        printLog("  → ${listItems.size} items created")
    }

    // ==================== Property ====================

    private suspend fun seedProperty() {
        if (propertyRepository.count() > 0) return

        printLog("Seeding property...")

        val listItems = ArrayList<Property>()
        EnumStatHelper.entries.forEach { stat ->
            listItems.add(
                Property(
                    code = stat.name,
                    name = stat.nameRu,
                    type = stat.type
                )
            )
        }

        transactionExecute { session ->
            propertyRepository.insertMany(listItems, session)
        }

        printLog("  → ${listItems.size} property created")
    }
}
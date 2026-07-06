package config

import application.enums.EnumStatHelper
import application.enums.EnumUserRoles
import config.MongoFactory.transactionExecute
import extensions.printLog
import features.character.Character
import features.character.CharacterRepository
import features.items.Items
import features.items.ItemsRepository
import features.property.Property
import features.property.PropertyRepository
import features.user.User
import features.user.UserRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue

/**
 * Заполнение БД начальными данными при старте сервера.
 *
 * Вызывать после DatabaseFactory.init().
 * Каждый блок проверяет, есть ли уже данные — повторный запуск безопасен.
 */
object DatabaseSeeder : KoinComponent {

    val userRepository: UserRepository by inject()
    val characterRepository: CharacterRepository by inject()
    val itemsRepository: ItemsRepository by inject()
    val propertyRepository: PropertyRepository by inject()

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
        seedItems()
        seedProperty()

        printLog("Database seeding completed")
    }

    // ==================== Users ====================

    private suspend fun seedUsers() {
        if (userRepository.count() > 0) return

        printLog("Seeding users...")

        val listItems = arrayListOf<User>()
        listItems.add(User(
            name = "Admin",
            email = "admin@game.com",
            age = 25,
            login = "admin",
            password = "P32543254",
            role = EnumUserRoles.ADMIN
        ))
        listItems.add(User(
            name = "TestPlayer",
            email = "player@game.com",
            age = 22,
            password = "P123456",
            login = "test1"
        ))

        transactionExecute { session ->
            userRepository.insertMany(listItems, session)
        }

        printLog("  → ${listItems.size} users created")
    }

    // ==================== Characters ====================

    private suspend fun seedCharacters() {
        if (characterRepository.count() > 0) return

        printLog("Seeding characters...")
        val userRepoAll = userRepository.findAll()

        val listItems = arrayListOf<Character>()
        listItems.add(Character(
            name = "Warrior",
            description = "STRONG pipster",
            userId = userRepoAll.first().getId(),
        ))
        listItems.add(Character(
            name = "Mage",
            description = "Мудрый pipster",
            userId = userRepoAll.last().getId(),
            level = 5,
            experience = 1200
        ))

        transactionExecute { session ->
            characterRepository.insertMany(listItems, session)
        }

        printLog("  → ${listItems.size} characters created")
    }

    // ==================== Equipment ====================
//
//    private fun seedEquipment() {
//        if (EquipmentTable.selectAll().count() > 0) return
//
//        printLog("Seeding equipment...")
//
//        EquipmentTable.insert {
//            it[name] = "Стальной шлем"
//            it[slot] = EnumEquipmentType.HELMET
//            it[rarity] = EnumRarity.COMMON
//            it[itemLevel] = 1
//            it[enhanceLevel] = 0
//            it[equippedSlot] = EnumEquipmentType.HELMET
//            it[stats] = mutableSetOf(
//                Stat(PropertyCache.getIdFromEnum(EnumStatHelper.STOCK_HEALTH), EnumStatType.STOCK,20.0),
//                Stat(PropertyCache.getIdFromEnum(EnumStatHelper.STOCK_AGILITY), EnumStatType.STOCK,3.0)
//            )
//            it[price] = 670u
//        }
//
//        EquipmentTable.insert {
//            it[name] = "Кираса дракона"
//            it[slot] = EnumEquipmentType.BODY
//            it[rarity] = EnumRarity.RARE
//            it[itemLevel] = 5
//            it[enhanceLevel] = 2
//            it[equippedSlot] = null
//            it[stats] = mutableSetOf(
//                Stat(PropertyCache.getIdFromEnum(EnumStatHelper.STOCK_HEALTH), EnumStatType.STOCK, 50.0),
//                Stat(PropertyCache.getIdFromEnum(EnumStatHelper.STOCK_INTELLECT), EnumStatType.STOCK,8.0),
//                Stat(PropertyCache.getIdFromEnum(EnumStatHelper.STOCK_ARMOR), EnumStatType.STOCK,10.0)
//            )
//            it[buffs] = mutableSetOf(
//                Stat(PropertyCache.getIdFromEnum(EnumStatHelper.STOCK_ATTACK_SPEED), EnumStatType.STOCK, 15.0)
//            )
//            it[price] = 8900u
//        }
//
//        EquipmentTable.insert {
//            it[name] = "Кольцо архимага"
//            it[slot] = EnumEquipmentType.RING
//            it[rarity] = EnumRarity.EPIC
//            it[itemLevel] = 10
//            it[enhanceLevel] = 0
//            it[equippedSlot] = EnumEquipmentType.RING
//            it[stats] = mutableSetOf(
//                Stat(PropertyCache.getIdFromEnum(EnumStatHelper.STOCK_INTELLECT), EnumStatType.STOCK,15.0),
//                Stat(PropertyCache.getIdFromEnum(EnumStatHelper.STOCK_ATTACK_PHYSICAL), EnumStatType.STOCK,12.0),
//                Stat(PropertyCache.getIdFromEnum(EnumStatHelper.STOCK_CRITICAL_CHANCE), EnumStatType.STOCK,5.0),
//                Stat(PropertyCache.getIdFromEnum(EnumStatHelper.STOCK_MANA), EnumStatType.STOCK,50.0)
//            )
//            it[price] = 4670u
//        }
//
//        printLog("  → 3 equipment items created")
//    }

    // ==================== Items ====================

    private suspend fun seedItems() {
        if (itemsRepository.count() > 0) return

        printLog("Seeding items...")
        val listItems = ArrayList<Items>()
        listItems.add(Items(
            name = "Дерево",
            description = "Кусок дерева (полено)",
            price = 10
        ))
        listItems.add(Items(
            name = "Камень",
            description = "Кучка кала",
            price = 12
        ))
        listItems.add(Items(
            name = "Зелье здоровья",
            description = "Восстанавливает здоровье",
            price = 80
        ))

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
            listItems.add(Property(
                code = stat.name,
                name = stat.nameRu,
                type = stat.type
            ))
        }

        transactionExecute { session ->
            propertyRepository.insertMany(listItems, session)
        }

        printLog("  → ${listItems.size} property created")
    }
}
package config

import application.enums.EnumEquipmentType
import application.enums.EnumRarity
import application.enums.EnumStatHelper
import application.enums.EnumStatType
import application.enums.EnumUserRoles
import application.model.CounterEntry
import application.model.Stat
import com.mongodb.kotlin.client.coroutine.ClientSession
import config.MongoFactory.transactionExecute
import extensions.printLog
import features.characterMongo.CharacterMongo
import features.characterMongo.CharacterMongoRepository
import features.equipment.EquipmentTable
import features.items.ItemsCache
import features.items.ItemsRepository
import features.items.ItemsTable
import features.property.PropertyCache
import features.property.PropertyRepository
import features.property.PropertyTable
import features.stats.CharacterStatsTable
import features.userMongo.UserMongo
import features.userMongo.UserRepositoryMongo
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Заполнение БД начальными данными при старте сервера.
 *
 * Вызывать после DatabaseFactory.init().
 * Каждый блок проверяет, есть ли уже данные — повторный запуск безопасен.
 */
object DatabaseSeeder {

    suspend fun seed() {
        printLog("Database seeding started")

        transaction {
            seedItems()
            seedProperty()
        }

        ItemsCache.refresh(ItemsRepository())
        PropertyCache.refresh(PropertyRepository())

        printLog("COUNT1: ${UserRepositoryMongo.count()} ${UserRepositoryMongo.findAll().size}")
        seedUsers()
        printLog("COUNT2: ${UserRepositoryMongo.count()} ${UserRepositoryMongo.findAll().size}")
        seedCharacters()
        printLog("COUNT3: ${UserRepositoryMongo.count()} ${UserRepositoryMongo.findAll().size}")

        transaction {
            seedEquipment()
            seedStats()
        }
        printLog("Database seeding completed")
    }

    // ==================== Users ====================

    private suspend fun seedUsers() {
        if (UserRepositoryMongo.count() > 0) return

        printLog("Seeding users...")

        val listItems = arrayListOf<UserMongo>()
        listItems.add(UserMongo(
            name = "Admin",
            email = "admin@game.com",
            age = 25,
            login = "admin",
            password = "P32543254",
            role = EnumUserRoles.ADMIN
        ))
        listItems.add(UserMongo(
            name = "TestPlayer",
            email = "player@game.com",
            age = 22,
            password = "P123456",
            login = "test1"
        ))

        transactionExecute { session ->
            UserRepositoryMongo.insertMany(listItems, session)
        }

        printLog("  → ${listItems.size} users created")
    }

    // ==================== Characters ====================

    private suspend fun seedCharacters() {
        if (CharacterMongoRepository.count() > 0) return

        printLog("Seeding characters...")
        val userRepoAll = UserRepositoryMongo.findAll()

        val listItems = arrayListOf<CharacterMongo>()
        listItems.add(CharacterMongo(
            name = "Warrior",
            description = "STRONG pipster",
            userId = userRepoAll.first().getId(),
        ))
        listItems.add(CharacterMongo(
            name = "Mage",
            description = "Мудрый pipster",
            userId = userRepoAll.last().getId(),
            level = 5,
            experience = 1200,
            params = mutableSetOf(
                Stat(PropertyCache.getIdFromEnum(EnumStatHelper.STOCK_HEALTH), EnumStatType.STOCK,80.0),
                Stat(PropertyCache.getIdFromEnum(EnumStatHelper.STOCK_STRENGTH), EnumStatType.STOCK,2.0),
                Stat(PropertyCache.getIdFromEnum(EnumStatHelper.STOCK_INVENTORY_SIZE), EnumStatType.STOCK,15.0),
            )
        ))

        transactionExecute { session ->
            CharacterMongoRepository.insertMany(listItems, session)
        }

        printLog("  → ${listItems.size} characters created")
    }

    // ==================== Equipment ====================

    private fun seedEquipment() {
        if (EquipmentTable.selectAll().count() > 0) return

        printLog("Seeding equipment...")

        EquipmentTable.insert {
            it[name] = "Стальной шлем"
            it[slot] = EnumEquipmentType.HELMET
            it[rarity] = EnumRarity.COMMON
            it[itemLevel] = 1
            it[enhanceLevel] = 0
            it[equippedSlot] = EnumEquipmentType.HELMET
            it[stats] = mutableSetOf(
                Stat(PropertyCache.getIdFromEnum(EnumStatHelper.STOCK_HEALTH), EnumStatType.STOCK,20.0),
                Stat(PropertyCache.getIdFromEnum(EnumStatHelper.STOCK_AGILITY), EnumStatType.STOCK,3.0)
            )
            it[price] = 670u
        }

        EquipmentTable.insert {
            it[name] = "Кираса дракона"
            it[slot] = EnumEquipmentType.BODY
            it[rarity] = EnumRarity.RARE
            it[itemLevel] = 5
            it[enhanceLevel] = 2
            it[equippedSlot] = null
            it[stats] = mutableSetOf(
                Stat(PropertyCache.getIdFromEnum(EnumStatHelper.STOCK_HEALTH), EnumStatType.STOCK, 50.0),
                Stat(PropertyCache.getIdFromEnum(EnumStatHelper.STOCK_INTELLECT), EnumStatType.STOCK,8.0),
                Stat(PropertyCache.getIdFromEnum(EnumStatHelper.STOCK_ARMOR), EnumStatType.STOCK,10.0)
            )
            it[buffs] = mutableSetOf(
                Stat(PropertyCache.getIdFromEnum(EnumStatHelper.STOCK_ATTACK_SPEED), EnumStatType.STOCK, 15.0)
            )
            it[price] = 8900u
        }

        EquipmentTable.insert {
            it[name] = "Кольцо архимага"
            it[slot] = EnumEquipmentType.RING
            it[rarity] = EnumRarity.EPIC
            it[itemLevel] = 10
            it[enhanceLevel] = 0
            it[equippedSlot] = EnumEquipmentType.RING
            it[stats] = mutableSetOf(
                Stat(PropertyCache.getIdFromEnum(EnumStatHelper.STOCK_INTELLECT), EnumStatType.STOCK,15.0),
                Stat(PropertyCache.getIdFromEnum(EnumStatHelper.STOCK_ATTACK_PHYSICAL), EnumStatType.STOCK,12.0),
                Stat(PropertyCache.getIdFromEnum(EnumStatHelper.STOCK_CRITICAL_CHANCE), EnumStatType.STOCK,5.0),
                Stat(PropertyCache.getIdFromEnum(EnumStatHelper.STOCK_MANA), EnumStatType.STOCK,50.0)
            )
            it[price] = 4670u
        }

        printLog("  → 3 equipment items created")
    }

    // ==================== Items ====================

    private fun seedItems() {
        if (ItemsTable.selectAll().count() > 0) return

        printLog("Seeding items...")

        ItemsTable.insert {
            it[name] = "Дерево"
            it[description] = "Кусок дерева (полено)"
            it[price] = 10u
        }

        ItemsTable.insert {
            it[name] = "Камень"
            it[description] = "Кучка кала"
            it[price] = 15u
        }

        ItemsTable.insert {
            it[name] = "Зелье здоровья"
            it[description] = "Восстанавливает здоровье"
            it[price] = 80u
        }

        printLog("  → 3 simple items created")
    }

    // ==================== Stats ====================

    private fun seedStats() {
        if (CharacterStatsTable.selectAll().count() > 0) return

        printLog("Seeding stats...")
//        val characterRepo = CharacterRepository().findAll()

        CharacterStatsTable.insert {
//            it[characterId] = characterRepo.first().id
            it[counters] = mutableSetOf(
                CounterEntry(PropertyCache.getIdFromEnum(EnumStatHelper.HISTORY_KILLS), 4),
                CounterEntry(PropertyCache.getIdFromEnum(EnumStatHelper.HISTORY_CRITICAL_HITS), 25),
            )
        }

        CharacterStatsTable.insert {
//            it[characterId] = characterRepo.last().id
            it[counters] = mutableSetOf(
                CounterEntry(PropertyCache.getIdFromEnum(EnumStatHelper.HISTORY_GOLD_GAINED), 5200),
            )
        }

        printLog("  → 2 stats created")
    }

    // ==================== Property ====================

    private fun seedProperty() {
        if (PropertyTable.selectAll().count() > 0) return

        printLog("Seeding property...")

        EnumStatHelper.entries.forEach { stat ->
            PropertyTable.insert {
                it[code] = stat.name
                it[name] = stat.nameRu
                it[type] = stat.type
            }
        }
    }
}
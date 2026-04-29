package config

import application.enums.EnumEquipmentType
import application.enums.EnumRarity
import application.enums.EnumStatHelper
import application.enums.EnumStatType
import application.enums.EnumUserRoles
import application.model.CounterEntry
import application.model.Stat
import extensions.printLog
import features.characters.CharacterRepository
import features.characters.CharacterTable
import features.equipment.EquipmentTable
import features.items.ItemsCache
import features.items.ItemsRepository
import features.items.ItemsTable
import features.modifier.ItemGenerationService
import features.property.PropertyCache
import features.property.PropertyRepository
import features.property.PropertyTable
import features.stats.CharacterStatsTable
import features.user.UserRepository
import features.user.UsersTable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Заполнение БД начальными данными при старте сервера.
 * Вызывать после DatabaseFactory.init().
 * Каждый блок проверяет наличие данных — повторный запуск безопасен.
 */
object DatabaseSeeder {

    fun seed() {
        printLog("Database seeding started")

        transaction {
            seedItems()
            seedProperty()
        }

        ItemsCache.refresh(ItemsRepository())
        PropertyCache.refresh(PropertyRepository())

        transaction {
            seedUsers()
            seedCharacters()
            seedEquipment()
            seedStats()
        }
        printLog("Database seeding completed")
    }

    // ==================== Users ====================

    private fun seedUsers() {
        if (UsersTable.selectAll().count() > 0) return
        printLog("Seeding users...")

        UsersTable.insert {
            it[name] = "Admin"
            it[email] = "admin@game.com"
            it[age] = 25
            it[login] = "admin"
            it[salt] = "${System.currentTimeMillis()}"
            it[password] = "pass1"
            it[isActive] = true
            it[role] = EnumUserRoles.ADMIN
        }

        UsersTable.insert {
            it[name] = "TestPlayer"
            it[email] = "player@game.com"
            it[age] = 20
            it[login] = "test1"
            it[salt] = "${System.currentTimeMillis()}"
            it[password] = "pass1"
            it[isActive] = true
            it[role] = EnumUserRoles.USER
        }

        printLog("  → 2 users created")
    }

    // ==================== Characters ====================

    private fun seedCharacters() {
        if (CharacterTable.selectAll().count() > 0) return
        printLog("Seeding characters...")

        val userRepoAll = UserRepository().findAll()

        CharacterTable.insert {
            it[name] = "Warrior"
            it[description] = "Могучий воин"
            it[userId] = userRepoAll.first().id
            it[level] = 1
            it[experience] = 0
        }

        CharacterTable.insert {
            it[name] = "Mage"
            it[description] = "Мудрый маг"
            it[userId] = userRepoAll.last().id
            it[level] = 5
            it[experience] = 1200
            it[params] = mutableSetOf(
                Stat(PropertyCache.getIdFromEnum(EnumStatHelper.STOCK_HEALTH), EnumStatType.STOCK, 80.0),
                Stat(PropertyCache.getIdFromEnum(EnumStatHelper.STOCK_STRENGTH), EnumStatType.STOCK, 2.0),
                Stat(PropertyCache.getIdFromEnum(EnumStatHelper.STOCK_INTELLECT), EnumStatType.STOCK, 15.0),
                Stat(PropertyCache.getIdFromEnum(EnumStatHelper.STOCK_MANA), EnumStatType.STOCK, 120.0),
                Stat(PropertyCache.getIdFromEnum(EnumStatHelper.STOCK_INVENTORY_SIZE), EnumStatType.STOCK, 15.0),
            )
        }

        printLog("  → 2 characters created")
    }

    // ==================== Equipment (сгенерированные через новую систему) ====================

    private fun seedEquipment() {
        if (EquipmentTable.selectAll().count() > 0) return
        printLog("Seeding equipment (PoE-style generation)...")

        val characters = CharacterRepository().findAll()
        val warrior = characters.first()
        val mage = characters.last()

        // Воин — шлем itemLevel 5 (UNCOMMON), кираса itemLevel 10 (RARE)
        listOf(
            Triple(warrior.id, EnumEquipmentType.HELMET, EnumRarity.UNCOMMON to 5),
            Triple(warrior.id, EnumEquipmentType.BODY,   EnumRarity.RARE     to 10),
            Triple(warrior.id, EnumEquipmentType.RING,   EnumRarity.COMMON   to 3),
        ).forEach { (charId, slot, rarityAndLevel) ->
            val (rarity, level) = rarityAndLevel
            val equipment = ItemGenerationService.generate(slot, level, rarity, charId)
            EquipmentTable.insert {
                it[name] = equipment.name
                it[EquipmentTable.slot] = equipment.slot
                it[EquipmentTable.rarity] = equipment.rarity
                it[itemLevel] = equipment.itemLevel
                it[enhanceLevel] = equipment.enhanceLevel
                it[characterId] = charId
                it[equippedSlot] = null
                it[price] = equipment.price
                it[implicit] = equipment.implicit
                it[modifiers] = equipment.modifiers
                it[description] = equipment.description
            }
        }

        // Маг — кольцо EPIC и сапоги RARE высокого уровня
        listOf(
            Triple(mage.id, EnumEquipmentType.RING,  EnumRarity.EPIC to 25),
            Triple(mage.id, EnumEquipmentType.BOOTS, EnumRarity.RARE to 20),
            Triple(mage.id, EnumEquipmentType.HELMET, EnumRarity.RARE to 15),
        ).forEach { (charId, slot, rarityAndLevel) ->
            val (rarity, level) = rarityAndLevel
            val equipment = ItemGenerationService.generate(slot, level, rarity, charId)
            EquipmentTable.insert {
                it[name] = equipment.name
                it[EquipmentTable.slot] = equipment.slot
                it[EquipmentTable.rarity] = equipment.rarity
                it[itemLevel] = equipment.itemLevel
                it[enhanceLevel] = equipment.enhanceLevel
                it[characterId] = charId
                it[equippedSlot] = null
                it[price] = equipment.price
                it[implicit] = equipment.implicit
                it[modifiers] = equipment.modifiers
                it[description] = equipment.description
            }
        }

        printLog("  → 6 equipment items generated and created")
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
            it[description] = "Кусок камня"
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

        val characterRepo = CharacterRepository().findAll()

        CharacterStatsTable.insert {
            it[characterId] = characterRepo.first().id
            it[counters] = mutableSetOf(
                CounterEntry(PropertyCache.getIdFromEnum(EnumStatHelper.HISTORY_KILLS), 4),
                CounterEntry(PropertyCache.getIdFromEnum(EnumStatHelper.HISTORY_CRITICAL_HITS), 25),
            )
        }

        CharacterStatsTable.insert {
            it[characterId] = characterRepo.last().id
            it[counters] = mutableSetOf(
                CounterEntry(PropertyCache.getIdFromEnum(EnumStatHelper.HISTORY_GOLD_GAINED), 5200),
            )
        }

        printLog("  → 2 stats records created")
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

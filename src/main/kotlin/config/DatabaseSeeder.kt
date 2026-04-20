package config

import application.enums.EnumCounter
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
import features.property.PropertyCache
import features.property.PropertyRepository
import features.property.PropertyTable
import features.stats.CharacterStatsTable
import features.user.UserRepository
import features.user.UsersTable
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
            it[salt] = "${System.currentTimeMillis()}}"
            it[password] = "pass1"
            it[isActive] = true
            it[role] = EnumUserRoles.ADMIN
        }

        UsersTable.insert {
            it[name] = "TestPlayer"
            it[email] = "player@game.com"
            it[age] = 20
            it[login] = "test1"
            it[salt] = "${System.currentTimeMillis()}}"
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
                Stat(PropertyCache.getFromCode("S_HEALTH")!!.id, EnumStatType.STOCK,80.0),
                Stat(PropertyCache.getFromCode("S_STR")!!.id, EnumStatType.STOCK,2.0),
                Stat(PropertyCache.getFromCode("S_INV")!!.id, EnumStatType.STOCK,15.0),
            )
        }

        printLog("  → 2 characters created")
    }

    // ==================== Equipment ====================

    private fun seedEquipment() {
        if (EquipmentTable.selectAll().count() > 0) return

        printLog("Seeding equipment...")
        val characterRepository = CharacterRepository().findAll()

        // Стальной шлем для Warrior
        EquipmentTable.insert {
            it[name] = "Стальной шлем"
            it[slot] = EnumEquipmentType.HELMET
            it[rarity] = EnumRarity.COMMON
            it[itemLevel] = 1
            it[enhanceLevel] = 0
            it[characterId] = characterRepository.first().id
            it[equippedSlot] = EnumEquipmentType.HELMET  // надет
            it[stats] = mutableSetOf(
                Stat(PropertyCache.getFromCode("S_HEALTH")!!.id, EnumStatType.STOCK,20.0),
                Stat(PropertyCache.getFromCode("S_AGI")!!.id, EnumStatType.STOCK,3.0)
            )
            it[price] = 670u
        }

        // Редкая кираса для Warrior (в сумке, не надета)
        EquipmentTable.insert {
            it[name] = "Кираса дракона"
            it[slot] = EnumEquipmentType.BODY
            it[rarity] = EnumRarity.RARE
            it[itemLevel] = 5
            it[enhanceLevel] = 2
            it[characterId] = characterRepository.last().id
            it[equippedSlot] = null  // в сумке
            it[stats] = mutableSetOf(
                Stat(PropertyCache.getFromCode("S_HEALTH")!!.id, EnumStatType.STOCK, 50.0),
                Stat(PropertyCache.getFromCode("S_INT")!!.id, EnumStatType.STOCK,8.0),
                Stat(PropertyCache.getFromCode("S_ARM")!!.id, EnumStatType.STOCK,10.0)
            )
            it[buffs] = mutableSetOf(
                Stat(PropertyCache.getFromCode("S_SPD")!!.id, EnumStatType.STOCK, 15.0)
            )
            it[price] = 8900u
        }

        // Эпическое кольцо для Mage
        EquipmentTable.insert {
            it[name] = "Кольцо архимага"
            it[slot] = EnumEquipmentType.RING
            it[rarity] = EnumRarity.EPIC
            it[itemLevel] = 10
            it[enhanceLevel] = 0
            it[characterId] = characterRepository.last().id
            it[equippedSlot] = EnumEquipmentType.RING  // надето
            it[stats] = mutableSetOf(
                Stat(PropertyCache.getFromCode("S_INT")!!.id, EnumStatType.STOCK,15.0),
                Stat(PropertyCache.getFromCode("S_FATK")!!.id, EnumStatType.STOCK,12.0),
                Stat(PropertyCache.getFromCode("S_CRIT_CH")!!.id, EnumStatType.STOCK,5.0),
                Stat(PropertyCache.getFromCode("S_MANA")!!.id, EnumStatType.STOCK,50.0)
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
        val characterRepo = CharacterRepository().findAll()

        CharacterStatsTable.insert {
            it[characterId] = characterRepo.first().id
            it[counters] = mutableSetOf(
                CounterEntry(EnumCounter.ITEMS_LOOTED, 4),
                CounterEntry(EnumCounter.POTIONS_USED, 2),
                CounterEntry(EnumCounter.DEATHS, 1),
            )
        }

        CharacterStatsTable.insert {
            it[characterId] = characterRepo.last().id
            it[counters] = mutableSetOf(
                CounterEntry(EnumCounter.DEATHS, 52),
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
                it[code] = stat.code
                it[name] = stat.nameRu
                it[type] = stat.type
            }
        }
    }
}
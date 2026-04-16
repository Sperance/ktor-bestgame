package config

import application.enums.EnumCounter
import application.enums.EnumEquipmentType
import application.enums.EnumRarity
import application.enums.EnumRecord
import application.enums.EnumStatType
import application.enums.EnumUserRoles
import application.model.CounterEntry
import application.model.ParamsStock
import application.model.RecordEntry
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
                ParamsStock(PropertyCache.getFromCode("HEALTH")!!.id, 80.0),
                ParamsStock(PropertyCache.getFromCode("STR")!!.id, 2.0),
                ParamsStock(PropertyCache.getFromCode("INV")!!.id, 15.0),
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
                ParamsStock(PropertyCache.getFromCode("HEALTH")!!.id, 20.0),
                ParamsStock(PropertyCache.getFromCode("AGI")!!.id, 3.0)
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
                ParamsStock(PropertyCache.getFromCode("HEALTH")!!.id, 50.0),
                ParamsStock(PropertyCache.getFromCode("INT")!!.id, 8.0),
                ParamsStock(PropertyCache.getFromCode("ARM")!!.id, 10.0)
            )
            it[buffs] = mutableSetOf(
                Stat(PropertyCache.getFromCode("SPD")!!.id, EnumStatType.PERCENT, 15.0)
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
                ParamsStock(PropertyCache.getFromCode("INT")!!.id, 15.0),
                ParamsStock(PropertyCache.getFromCode("FATK")!!.id, 12.0),
                ParamsStock(PropertyCache.getFromCode("CRIT_CH")!!.id, 5.0),
                ParamsStock(PropertyCache.getFromCode("MANA")!!.id, 50.0)
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
            it[records] = mutableSetOf()
        }

        CharacterStatsTable.insert {
            it[characterId] = characterRepo.last().id
            it[counters] = mutableSetOf(
                CounterEntry(EnumCounter.DEATHS, 52),
            )
            it[records] = mutableSetOf(RecordEntry(EnumRecord.HIGHEST_FLOOR, 14))
        }

        printLog("  → 2 stats created")
    }

    // ==================== Property ====================

    private fun seedProperty() {
        if (PropertyTable.selectAll().count() > 0) return

        printLog("Seeding property...")

        PropertyTable.insert {
            it[code] = "HEALTH"
            it[name] = "Здоровье"
        }
        PropertyTable.insert {
            it[code] = "MANA"
            it[name] = "Мана"
        }
        PropertyTable.insert {
            it[code] = "ENERGY"
            it[name] = "Энергия"
        }
        PropertyTable.insert {
            it[code] = "STR"
            it[name] = "Сила"
        }
        PropertyTable.insert {
            it[code] = "AGI"
            it[name] = "Ловкость"
        }
        PropertyTable.insert {
            it[code] = "INT"
            it[name] = "Интеллект"
        }
        PropertyTable.insert {
            it[code] = "INV"
            it[name] = "Размер инвентаря"
        }
        PropertyTable.insert {
            it[code] = "CRIT_CH"
            it[name] = "Шанс критического удара"
        }
        PropertyTable.insert {
            it[code] = "CRIT"
            it[name] = "Урон критического удара"
        }
        PropertyTable.insert {
            it[code] = "SPD"
            it[name] = "Скорость атаки"
        }
        PropertyTable.insert {
            it[code] = "ARM"
            it[name] = "Броня"
        }
        PropertyTable.insert {
            it[code] = "FATK"
            it[name] = "Физический урон"
        }
        PropertyTable.insert {
            it[code] = "MATK"
            it[name] = "Магический урон"
        }
    }
}
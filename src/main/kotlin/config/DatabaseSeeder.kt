package config

import application.enums.EnumEquipmentType
import application.enums.EnumRarity
import application.enums.EnumStatKey
import application.enums.EnumStatType
import application.enums.EnumUserRoles
import application.model.ParamsStock
import application.model.Stat
import extensions.printLog
import features.characters.CharacterTable
import features.equipment.EquipmentTable
import features.items.ItemsTable
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
        transaction {
            seedUsers()
            seedCharacters()
            seedEquipment()
            seedItems()
        }
        printLog("Database seeding complete")
    }

    // ==================== Users ====================

    private fun seedUsers() {
        if (UsersTable.selectAll().count() > 0) return

        printLog("Seeding users... ${UsersTable.selectAll().count()}")

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
            it[userId] = userRepoAll.first().id!!
            it[level] = 1
            it[experience] = 0
        }

        CharacterTable.insert {
            it[name] = "Mage"
            it[description] = "Мудрый маг"
            it[userId] = userRepoAll.last().id!!
            it[level] = 5
            it[experience] = 1200
            it[params] = mutableSetOf(
                ParamsStock(EnumStatKey.LIFE, 80.0),
                ParamsStock(EnumStatKey.STR, 2.0),
                ParamsStock(EnumStatKey.DEX, 3.0),
                ParamsStock(EnumStatKey.INT, 10.0),
                ParamsStock(EnumStatKey.INVENTORY_SIZE, 15.0),
                ParamsStock(EnumStatKey.CRIT_DAMAGE, 200.0),
                ParamsStock(EnumStatKey.ATTACK_SPEED, 1.2),
            )
        }

        printLog("  → 2 characters created")
    }

    // ==================== Equipment ====================

    private fun seedEquipment() {
//        if (EquipmentTable.selectAll().count() > 0) return

        printLog("Seeding equipment...")

        // Стальной шлем для Warrior
        EquipmentTable.insert {
            it[name] = "Стальной шлем"
            it[slot] = EnumEquipmentType.HELMET
            it[rarity] = EnumRarity.COMMON
            it[itemLevel] = 1
            it[enhanceLevel] = 0
            it[characterId] = 2L
            it[equippedSlot] = EnumEquipmentType.HELMET  // надет
            it[stats] = mutableSetOf(
                ParamsStock(EnumStatKey.LIFE, 20.0),
                ParamsStock(EnumStatKey.STR, 3.0)
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
            it[characterId] = 1L
            it[equippedSlot] = null  // в сумке
            it[stats] = mutableSetOf(
                ParamsStock(EnumStatKey.LIFE, 50.0),
                ParamsStock(EnumStatKey.STR, 8.0),
                ParamsStock(EnumStatKey.MAGIC_RESIST, 10.0)
            )
            it[buffs] = mutableSetOf(
                Stat(EnumStatKey.FIRE_RESIST, EnumStatType.PERCENT, 15.0)
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
            it[characterId] = 2L
            it[equippedSlot] = EnumEquipmentType.RING  // надето
            it[stats] = mutableSetOf(
                ParamsStock(EnumStatKey.INT, 15.0),
                ParamsStock(EnumStatKey.MAGICAL_DAMAGE, 12.0),
                ParamsStock(EnumStatKey.CRIT_CHANCE, 5.0),
                ParamsStock(EnumStatKey.MANA, 50.0)
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
}
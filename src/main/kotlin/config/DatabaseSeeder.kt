package config

import application.enums.EnumEquipmentType
import application.enums.EnumRarity
import application.enums.EnumStatHelper
import application.enums.EnumUserRoles
import com.mongodb.kotlin.client.coroutine.ClientSession
import config.MongoFactory.transactionExecute
import extensions.printLog
import features.data.character.Character
import features.data.character.CharacterRepository
import features.data.equipment.EquipmentRepository
import features.data.equipmentName.EquipmentName
import features.data.equipmentName.EquipmentNameRepository
import features.data.items.Items
import features.data.items.ItemsRepository
import features.data.property.Property
import features.data.blockList.BlockListRepository
import features.data.items.ItemType
import features.data.property.PropertyRepository
import features.data.recipe.Recipe
import features.data.recipe.RecipeRepository
import features.data.user.User
import features.data.user.UserRepository
import features.logic.EquipmentGenerator
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
    private val propertyRepository: PropertyRepository by inject()
    private val equipmentRepository: EquipmentRepository by inject()
    private val equipmentNameRepository: EquipmentNameRepository by inject()
    private val blockListRepository: BlockListRepository by inject()
    private val recipeRepository: RecipeRepository by inject()

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
            seedProperty(session)
            seedEquipmentName(session)
            seedEquipment(session)
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

        val generator = EquipmentGenerator()
        val listItems = generator.generateMultipleEquipment(null, 5)

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
                experience = 1200,
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
                category = ItemType("WOOD_STOCK", "LOG"), description = "Кусок дерева (полено)", price = 10, name = "Дрееово жыжы"
            )
        )
        listItems.add(
            Items(
                category = ItemType("STONE_STOCK", "STONE"), description = "Кучка кала", price = 12, name = "Кал"
            )
        )
        listItems.add(
            Items(
                category = ItemType("STONE_STOCK", "STONE"), description = "Кучка кала 2", price = 22, name = "Кал23"
            )
        )
        listItems.add(
            Items(
                category = ItemType("CONSUMABLE", "HEALTH"), description = "Восстанавливает здоровье", price = 80, name = "Зелье"
            )
        )

        itemsRepository.insertMany(listItems, session)

        printLog("  → ${listItems.size} items created")
    }

    // ==================== Property ====================

    private suspend fun seedProperty(session: ClientSession) {
        if (propertyRepository.count() > 0) return

        printLog("Seeding property...")

        val listItems = ArrayList<Property>()
        EnumStatHelper.entries.forEach { stat ->
            listItems.add(
                Property(
                    code = stat.name, name = stat.nameRu, type = stat.type, step = stat.step
                )
            )
        }

        propertyRepository.insertMany(listItems, session)

        printLog("  → ${listItems.size} property created")
    }

    private suspend fun seedEquipmentName(session: ClientSession) {
        if (equipmentNameRepository.count() > 0) return

        printLog("Seeding EquipmentName...")

        val listItems = ArrayList<EquipmentName>()

        // ==================== HELMETS ====================
        listItems.addAll(
            listOf(
                // COMMON
                EquipmentName(
                    "Leather Cap", EnumEquipmentType.HELMET, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Iron Skullcap", EnumEquipmentType.HELMET, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Hide Helm", EnumEquipmentType.HELMET, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Chain Coif", EnumEquipmentType.HELMET, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Sallet", EnumEquipmentType.HELMET, EnumRarity.COMMON
                ),
                // UNCOMMON
                EquipmentName(
                    "Steel Helm", EnumEquipmentType.HELMET, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Knight's Casque", EnumEquipmentType.HELMET, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Bronze Greathelm", EnumEquipmentType.HELMET, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Visored Helm", EnumEquipmentType.HELMET, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Warden's Crown", EnumEquipmentType.HELMET, EnumRarity.UNCOMMON
                ),
                // RARE
                EquipmentName(
                    "Helm of Valor", EnumEquipmentType.HELMET, EnumRarity.RARE
                ),
                EquipmentName(
                    "Battle Mask", EnumEquipmentType.HELMET, EnumRarity.RARE
                ),
                EquipmentName(
                    "Aegis Helm", EnumEquipmentType.HELMET, EnumRarity.RARE
                ),
                EquipmentName(
                    "Fury's Visage", EnumEquipmentType.HELMET, EnumRarity.RARE
                ),
                EquipmentName(
                    "Warrior's Sallet", EnumEquipmentType.HELMET, EnumRarity.RARE
                ),
                // EPIC
                EquipmentName(
                    "Helm of Justice", EnumEquipmentType.HELMET, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Crown of Glory", EnumEquipmentType.HELMET, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Helm of the Martyr", EnumEquipmentType.HELMET, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Radiant Casque", EnumEquipmentType.HELMET, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Helm of Enlightenment", EnumEquipmentType.HELMET, EnumRarity.EPIC
                ),
                // LEGENDARY
                EquipmentName(
                    "Azure Crown", EnumEquipmentType.HELMET, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Helm of the Titan", EnumEquipmentType.HELMET, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Dragonlord's Helm", EnumEquipmentType.HELMET, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Helm of Immortality", EnumEquipmentType.HELMET, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Legendary Casque", EnumEquipmentType.HELMET, EnumRarity.LEGENDARY
                ),
                // MYTHICAL
                EquipmentName(
                    "Circlet of Eternity", EnumEquipmentType.HELMET, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Helm of the Divine", EnumEquipmentType.HELMET, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Creator's Crown", EnumEquipmentType.HELMET, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Halo of the Immortal", EnumEquipmentType.HELMET, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Helm of Destiny", EnumEquipmentType.HELMET, EnumRarity.MYTHICAL
                ),
            )
        )

        // ==================== BODY ARMOR ====================
        listItems.addAll(
            listOf(
                // COMMON
                EquipmentName(
                    "Leather Jerkin", EnumEquipmentType.BODY, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Padded Tunic", EnumEquipmentType.BODY, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Simple Breastplate", EnumEquipmentType.BODY, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Studded Leather", EnumEquipmentType.BODY, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Gambeson", EnumEquipmentType.BODY, EnumRarity.COMMON
                ),
                // UNCOMMON
                EquipmentName(
                    "Chainmail Hauberk", EnumEquipmentType.BODY, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Steel Cuirass", EnumEquipmentType.BODY, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Bronze Platemail", EnumEquipmentType.BODY, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Guardian's Armor", EnumEquipmentType.BODY, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Forged Breastplate", EnumEquipmentType.BODY, EnumRarity.UNCOMMON
                ),
                // RARE
                EquipmentName(
                    "Warbringer Armor", EnumEquipmentType.BODY, EnumRarity.RARE
                ),
                EquipmentName(
                    "Aegis Cuirass", EnumEquipmentType.BODY, EnumRarity.RARE
                ),
                EquipmentName(
                    "Berserker's Chain", EnumEquipmentType.BODY, EnumRarity.RARE
                ),
                EquipmentName(
                    "Champion's Platemail", EnumEquipmentType.BODY, EnumRarity.RARE
                ),
                EquipmentName(
                    "Armor of Fortitude", EnumEquipmentType.BODY, EnumRarity.RARE
                ),
                // EPIC
                EquipmentName(
                    "Armor of Justice", EnumEquipmentType.BODY, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Cuirass of Glory", EnumEquipmentType.BODY, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Martyr's Platemail", EnumEquipmentType.BODY, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Radiant Hauberk", EnumEquipmentType.BODY, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Armor of Enlightenment", EnumEquipmentType.BODY, EnumRarity.EPIC
                ),
                // LEGENDARY
                EquipmentName(
                    "Azure Cuirass", EnumEquipmentType.BODY, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Titan's Platemail", EnumEquipmentType.BODY, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Dragonlord's Hauberk", EnumEquipmentType.BODY, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Armor of Immortality", EnumEquipmentType.BODY, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Legendary Platemail", EnumEquipmentType.BODY, EnumRarity.LEGENDARY
                ),
                // MYTHICAL
                EquipmentName(
                    "Cuirass of Eternity", EnumEquipmentType.BODY, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Divine Platemail", EnumEquipmentType.BODY, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Creator's Hauberk", EnumEquipmentType.BODY, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Armor of the Immortal", EnumEquipmentType.BODY, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Platemail of Destiny", EnumEquipmentType.BODY, EnumRarity.MYTHICAL
                ),
            )
        )

        // ==================== GLOVES ====================
        listItems.addAll(
            listOf(
                // COMMON
                EquipmentName(
                    "Leather Gloves", EnumEquipmentType.GLOVES, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Cloth Wraps", EnumEquipmentType.GLOVES, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Simple Gauntlets", EnumEquipmentType.GLOVES, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Hide Gloves", EnumEquipmentType.GLOVES, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Chain Gloves", EnumEquipmentType.GLOVES, EnumRarity.COMMON
                ),
                // UNCOMMON
                EquipmentName(
                    "Steel Gauntlets", EnumEquipmentType.GLOVES, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Bronze Handguards", EnumEquipmentType.GLOVES, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Warden's Grips", EnumEquipmentType.GLOVES, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Forged Gauntlets", EnumEquipmentType.GLOVES, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Knight's Gloves", EnumEquipmentType.GLOVES, EnumRarity.UNCOMMON
                ),
                // RARE
                EquipmentName(
                    "Warbringer Gauntlets", EnumEquipmentType.GLOVES, EnumRarity.RARE
                ),
                EquipmentName(
                    "Aegis Handguards", EnumEquipmentType.GLOVES, EnumRarity.RARE
                ),
                EquipmentName(
                    "Berserker's Grips", EnumEquipmentType.GLOVES, EnumRarity.RARE
                ),
                EquipmentName(
                    "Champion's Gauntlets", EnumEquipmentType.GLOVES, EnumRarity.RARE
                ),
                EquipmentName(
                    "Gloves of Fortitude", EnumEquipmentType.GLOVES, EnumRarity.RARE
                ),
                // EPIC
                EquipmentName(
                    "Gauntlets of Justice", EnumEquipmentType.GLOVES, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Handguards of Glory", EnumEquipmentType.GLOVES, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Martyr's Gauntlets", EnumEquipmentType.GLOVES, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Radiant Gloves", EnumEquipmentType.GLOVES, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Gauntlets of Enlightenment", EnumEquipmentType.GLOVES, EnumRarity.EPIC
                ),
                // LEGENDARY
                EquipmentName(
                    "Azure Gauntlets", EnumEquipmentType.GLOVES, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Titan's Handguards", EnumEquipmentType.GLOVES, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Dragonlord's Gauntlets", EnumEquipmentType.GLOVES, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Gloves of Immortality", EnumEquipmentType.GLOVES, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Legendary Gauntlets", EnumEquipmentType.GLOVES, EnumRarity.LEGENDARY
                ),
                // MYTHICAL
                EquipmentName(
                    "Gauntlets of Eternity", EnumEquipmentType.GLOVES, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Divine Handguards", EnumEquipmentType.GLOVES, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Creator's Gauntlets", EnumEquipmentType.GLOVES, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Gloves of the Immortal", EnumEquipmentType.GLOVES, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Gauntlets of Destiny", EnumEquipmentType.GLOVES, EnumRarity.MYTHICAL
                ),
            )
        )

        // ==================== RINGS ====================
        listItems.addAll(
            listOf(
                // COMMON
                EquipmentName(
                    "Simple Ring", EnumEquipmentType.RING, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Copper Band", EnumEquipmentType.RING, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Tin Ring", EnumEquipmentType.RING, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Brass Loop", EnumEquipmentType.RING, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Iron Ring", EnumEquipmentType.RING, EnumRarity.COMMON
                ),
                // UNCOMMON
                EquipmentName(
                    "Silver Ring", EnumEquipmentType.RING, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Bronze Band", EnumEquipmentType.RING, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Agate Ring", EnumEquipmentType.RING, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Turquoise Loop", EnumEquipmentType.RING, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Gold Ring", EnumEquipmentType.RING, EnumRarity.UNCOMMON
                ),
                // RARE
                EquipmentName(
                    "Ring of Might", EnumEquipmentType.RING, EnumRarity.RARE
                ),
                EquipmentName(
                    "Ring of Wisdom", EnumEquipmentType.RING, EnumRarity.RARE
                ),
                EquipmentName(
                    "Ring of Protection", EnumEquipmentType.RING, EnumRarity.RARE
                ),
                EquipmentName(
                    "Ring of Agility", EnumEquipmentType.RING, EnumRarity.RARE
                ),
                EquipmentName(
                    "Ring of Endurance", EnumEquipmentType.RING, EnumRarity.RARE
                ),
                // EPIC
                EquipmentName(
                    "Ring of Valor", EnumEquipmentType.RING, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Ring of Justice", EnumEquipmentType.RING, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Ring of the Champion", EnumEquipmentType.RING, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Ring of Radiance", EnumEquipmentType.RING, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Ring of Fate", EnumEquipmentType.RING, EnumRarity.EPIC
                ),
                // LEGENDARY
                EquipmentName(
                    "Azure Ring", EnumEquipmentType.RING, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Ring of the Titan", EnumEquipmentType.RING, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Dragonlord's Band", EnumEquipmentType.RING, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Ring of Immortality", EnumEquipmentType.RING, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Legendary Ring", EnumEquipmentType.RING, EnumRarity.LEGENDARY
                ),
                // MYTHICAL
                EquipmentName(
                    "Ring of Eternity", EnumEquipmentType.RING, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Divine Band", EnumEquipmentType.RING, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Creator's Ring", EnumEquipmentType.RING, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Ring of the Immortal", EnumEquipmentType.RING, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Ring of Destiny", EnumEquipmentType.RING, EnumRarity.MYTHICAL
                ),
            )
        )

        // ==================== BOOTS ====================
        listItems.addAll(
            listOf(
                // COMMON
                EquipmentName(
                    "Leather Boots", EnumEquipmentType.BOOTS, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Cloth Footwraps", EnumEquipmentType.BOOTS, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Simple Boots", EnumEquipmentType.BOOTS, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Hide Boots", EnumEquipmentType.BOOTS, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Chain Boots", EnumEquipmentType.BOOTS, EnumRarity.COMMON
                ),
                // UNCOMMON
                EquipmentName(
                    "Steel Greaves", EnumEquipmentType.BOOTS, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Bronze Boots", EnumEquipmentType.BOOTS, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Warden's Boots", EnumEquipmentType.BOOTS, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Forged Greaves", EnumEquipmentType.BOOTS, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Knight's Sabatons", EnumEquipmentType.BOOTS, EnumRarity.UNCOMMON
                ),
                // RARE
                EquipmentName(
                    "Warbringer Boots", EnumEquipmentType.BOOTS, EnumRarity.RARE
                ),
                EquipmentName(
                    "Aegis Greaves", EnumEquipmentType.BOOTS, EnumRarity.RARE
                ),
                EquipmentName(
                    "Berserker's Boots", EnumEquipmentType.BOOTS, EnumRarity.RARE
                ),
                EquipmentName(
                    "Champion's Sabatons", EnumEquipmentType.BOOTS, EnumRarity.RARE
                ),
                EquipmentName(
                    "Boots of Fortitude", EnumEquipmentType.BOOTS, EnumRarity.RARE
                ),
                // EPIC
                EquipmentName(
                    "Greaves of Justice", EnumEquipmentType.BOOTS, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Boots of Glory", EnumEquipmentType.BOOTS, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Martyr's Sabatons", EnumEquipmentType.BOOTS, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Radiant Boots", EnumEquipmentType.BOOTS, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Sabatons of Enlightenment", EnumEquipmentType.BOOTS, EnumRarity.EPIC
                ),
                // LEGENDARY
                EquipmentName(
                    "Azure Greaves", EnumEquipmentType.BOOTS, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Titan's Boots", EnumEquipmentType.BOOTS, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Dragonlord's Sabatons", EnumEquipmentType.BOOTS, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Boots of Immortality", EnumEquipmentType.BOOTS, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Legendary Greaves", EnumEquipmentType.BOOTS, EnumRarity.LEGENDARY
                ),
                // MYTHICAL
                EquipmentName(
                    "Boots of Eternity", EnumEquipmentType.BOOTS, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Divine Sabatons", EnumEquipmentType.BOOTS, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Creator's Greaves", EnumEquipmentType.BOOTS, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Boots of the Immortal", EnumEquipmentType.BOOTS, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Sabatons of Destiny", EnumEquipmentType.BOOTS, EnumRarity.MYTHICAL
                ),
            )
        )

        // ==================== WINGS ====================
        listItems.addAll(
            listOf(
                // COMMON
                EquipmentName(
                    "Raven Wings", EnumEquipmentType.WINGS, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Owl Wings", EnumEquipmentType.WINGS, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Gull Wings", EnumEquipmentType.WINGS, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Dove Wings", EnumEquipmentType.WINGS, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Hawk Wings", EnumEquipmentType.WINGS, EnumRarity.COMMON
                ),
                // UNCOMMON
                EquipmentName(
                    "Eagle Wings", EnumEquipmentType.WINGS, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Falcon Wings", EnumEquipmentType.WINGS, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Kite Wings", EnumEquipmentType.WINGS, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Peregrine Wings", EnumEquipmentType.WINGS, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Golden Eagle Wings", EnumEquipmentType.WINGS, EnumRarity.UNCOMMON
                ),
                // RARE
                EquipmentName(
                    "Storm Wings", EnumEquipmentType.WINGS, EnumRarity.RARE
                ),
                EquipmentName(
                    "Wind Wings", EnumEquipmentType.WINGS, EnumRarity.RARE
                ),
                EquipmentName(
                    "Thunder Wings", EnumEquipmentType.WINGS, EnumRarity.RARE
                ),
                EquipmentName(
                    "Lightning Wings", EnumEquipmentType.WINGS, EnumRarity.RARE
                ),
                EquipmentName(
                    "Hurricane Wings", EnumEquipmentType.WINGS, EnumRarity.RARE
                ),
                // EPIC
                EquipmentName(
                    "Wings of Valor", EnumEquipmentType.WINGS, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Wings of Justice", EnumEquipmentType.WINGS, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Champion's Wings", EnumEquipmentType.WINGS, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Radiant Wings", EnumEquipmentType.WINGS, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Wings of Enlightenment", EnumEquipmentType.WINGS, EnumRarity.EPIC
                ),
                // LEGENDARY
                EquipmentName(
                    "Azure Wings", EnumEquipmentType.WINGS, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Titan's Wings", EnumEquipmentType.WINGS, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Dragonlord's Wings", EnumEquipmentType.WINGS, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Wings of Immortality", EnumEquipmentType.WINGS, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Legendary Wings", EnumEquipmentType.WINGS, EnumRarity.LEGENDARY
                ),
                // MYTHICAL
                EquipmentName(
                    "Wings of Eternity", EnumEquipmentType.WINGS, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Divine Wings", EnumEquipmentType.WINGS, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Creator's Wings", EnumEquipmentType.WINGS, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Wings of the Immortal", EnumEquipmentType.WINGS, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Wings of Destiny", EnumEquipmentType.WINGS, EnumRarity.MYTHICAL
                ),
            )
        )

        // ==================== BELTS ====================
        listItems.addAll(
            listOf(
                // COMMON
                EquipmentName(
                    "Leather Belt", EnumEquipmentType.BELT, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Cloth Sash", EnumEquipmentType.BELT, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Simple Belt", EnumEquipmentType.BELT, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Braided Belt", EnumEquipmentType.BELT, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Hide Belt", EnumEquipmentType.BELT, EnumRarity.COMMON
                ),
                // UNCOMMON
                EquipmentName(
                    "Steel Buckle Belt", EnumEquipmentType.BELT, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Bronze Girdle", EnumEquipmentType.BELT, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Warden's Belt", EnumEquipmentType.BELT, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Forged Belt", EnumEquipmentType.BELT, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Knight's Cincture", EnumEquipmentType.BELT, EnumRarity.UNCOMMON
                ),
                // RARE
                EquipmentName(
                    "Warbringer Belt", EnumEquipmentType.BELT, EnumRarity.RARE
                ),
                EquipmentName(
                    "Aegis Girdle", EnumEquipmentType.BELT, EnumRarity.RARE
                ),
                EquipmentName(
                    "Berserker's Belt", EnumEquipmentType.BELT, EnumRarity.RARE
                ),
                EquipmentName(
                    "Champion's Cincture", EnumEquipmentType.BELT, EnumRarity.RARE
                ),
                EquipmentName(
                    "Belt of Fortitude", EnumEquipmentType.BELT, EnumRarity.RARE
                ),
                // EPIC
                EquipmentName(
                    "Belt of Justice", EnumEquipmentType.BELT, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Girdle of Glory", EnumEquipmentType.BELT, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Martyr's Cincture", EnumEquipmentType.BELT, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Radiant Belt", EnumEquipmentType.BELT, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Sash of Enlightenment", EnumEquipmentType.BELT, EnumRarity.EPIC
                ),
                // LEGENDARY
                EquipmentName(
                    "Azure Girdle", EnumEquipmentType.BELT, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Titan's Belt", EnumEquipmentType.BELT, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Dragonlord's Cincture", EnumEquipmentType.BELT, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Belt of Immortality", EnumEquipmentType.BELT, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Legendary Girdle", EnumEquipmentType.BELT, EnumRarity.LEGENDARY
                ),
                // MYTHICAL
                EquipmentName(
                    "Belt of Eternity", EnumEquipmentType.BELT, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Divine Cincture", EnumEquipmentType.BELT, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Creator's Belt", EnumEquipmentType.BELT, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Girdle of the Immortal", EnumEquipmentType.BELT, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Belt of Destiny", EnumEquipmentType.BELT, EnumRarity.MYTHICAL
                ),
            )
        )

        // ==================== ONE-HANDED WEAPONS ====================
        listItems.addAll(
            listOf(
                // COMMON
                EquipmentName(
                    "Shortsword", EnumEquipmentType.WEAPON_1H, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Hand Axe", EnumEquipmentType.WEAPON_1H, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Dagger", EnumEquipmentType.WEAPON_1H, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Flail", EnumEquipmentType.WEAPON_1H, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Mace", EnumEquipmentType.WEAPON_1H, EnumRarity.COMMON
                ),
                // UNCOMMON
                EquipmentName(
                    "Steel Saber", EnumEquipmentType.WEAPON_1H, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Silver Rapier", EnumEquipmentType.WEAPON_1H, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Bronze War Axe", EnumEquipmentType.WEAPON_1H, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Scimitar", EnumEquipmentType.WEAPON_1H, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Warden's Blade", EnumEquipmentType.WEAPON_1H, EnumRarity.UNCOMMON
                ),
                // RARE
                EquipmentName(
                    "Champion's Sword", EnumEquipmentType.WEAPON_1H, EnumRarity.RARE
                ),
                EquipmentName(
                    "Fury's Edge", EnumEquipmentType.WEAPON_1H, EnumRarity.RARE
                ),
                EquipmentName(
                    "Battle Axe", EnumEquipmentType.WEAPON_1H, EnumRarity.RARE
                ),
                EquipmentName(
                    "Shadow Dagger", EnumEquipmentType.WEAPON_1H, EnumRarity.RARE
                ),
                EquipmentName(
                    "Storm Mace", EnumEquipmentType.WEAPON_1H, EnumRarity.RARE
                ),
                // EPIC
                EquipmentName(
                    "Sword of Valor", EnumEquipmentType.WEAPON_1H, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Blade of Justice", EnumEquipmentType.WEAPON_1H, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Champion's Axe", EnumEquipmentType.WEAPON_1H, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Dagger of Radiance", EnumEquipmentType.WEAPON_1H, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Mace of Enlightenment", EnumEquipmentType.WEAPON_1H, EnumRarity.EPIC
                ),
                // LEGENDARY
                EquipmentName(
                    "Azure Sword", EnumEquipmentType.WEAPON_1H, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Titan's Blade", EnumEquipmentType.WEAPON_1H, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Dragonlord's Axe", EnumEquipmentType.WEAPON_1H, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Dagger of Immortality", EnumEquipmentType.WEAPON_1H, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Legendary Saber", EnumEquipmentType.WEAPON_1H, EnumRarity.LEGENDARY
                ),
                // MYTHICAL
                EquipmentName(
                    "Sword of Eternity", EnumEquipmentType.WEAPON_1H, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Divine Blade", EnumEquipmentType.WEAPON_1H, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Creator's Axe", EnumEquipmentType.WEAPON_1H, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Dagger of Destiny", EnumEquipmentType.WEAPON_1H, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Blade of the Immortal", EnumEquipmentType.WEAPON_1H, EnumRarity.MYTHICAL
                ),
            )
        )

        // ==================== TWO-HANDED WEAPONS ====================
        listItems.addAll(
            listOf(
                // COMMON
                EquipmentName(
                    "Greatsword", EnumEquipmentType.WEAPON_2H, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Battle Greataxe", EnumEquipmentType.WEAPON_2H, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Simple Halberd", EnumEquipmentType.WEAPON_2H, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Glaive", EnumEquipmentType.WEAPON_2H, EnumRarity.COMMON
                ),
                EquipmentName(
                    "War Hammer", EnumEquipmentType.WEAPON_2H, EnumRarity.COMMON
                ),
                // UNCOMMON
                EquipmentName(
                    "Steel Zweihander", EnumEquipmentType.WEAPON_2H, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Silver Claymore", EnumEquipmentType.WEAPON_2H, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Bronze Great Axe", EnumEquipmentType.WEAPON_2H, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Warden's Halberd", EnumEquipmentType.WEAPON_2H, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Forged Warhammer", EnumEquipmentType.WEAPON_2H, EnumRarity.UNCOMMON
                ),
                // RARE
                EquipmentName(
                    "Giant's Greatsword", EnumEquipmentType.WEAPON_2H, EnumRarity.RARE
                ),
                EquipmentName(
                    "Fury's Greataxe", EnumEquipmentType.WEAPON_2H, EnumRarity.RARE
                ),
                EquipmentName(
                    "Storm Glaive", EnumEquipmentType.WEAPON_2H, EnumRarity.RARE
                ),
                EquipmentName(
                    "Thunder Hammer", EnumEquipmentType.WEAPON_2H, EnumRarity.RARE
                ),
                EquipmentName(
                    "Halberd of War", EnumEquipmentType.WEAPON_2H, EnumRarity.RARE
                ),
                // EPIC
                EquipmentName(
                    "Greatsword of Valor", EnumEquipmentType.WEAPON_2H, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Claymore of Justice", EnumEquipmentType.WEAPON_2H, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Champion's Greataxe", EnumEquipmentType.WEAPON_2H, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Hammer of Radiance", EnumEquipmentType.WEAPON_2H, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Halberd of Enlightenment", EnumEquipmentType.WEAPON_2H, EnumRarity.EPIC
                ),
                // LEGENDARY
                EquipmentName(
                    "Azure Greatsword", EnumEquipmentType.WEAPON_2H, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Titan's Claymore", EnumEquipmentType.WEAPON_2H, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Dragonlord's Greataxe", EnumEquipmentType.WEAPON_2H, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Hammer of Immortality", EnumEquipmentType.WEAPON_2H, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Legendary Zweihander", EnumEquipmentType.WEAPON_2H, EnumRarity.LEGENDARY
                ),
                // MYTHICAL
                EquipmentName(
                    "Greatsword of Eternity", EnumEquipmentType.WEAPON_2H, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Divine Claymore", EnumEquipmentType.WEAPON_2H, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Creator's Greataxe", EnumEquipmentType.WEAPON_2H, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Hammer of Destiny", EnumEquipmentType.WEAPON_2H, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Zweihander of the Immortal", EnumEquipmentType.WEAPON_2H, EnumRarity.MYTHICAL
                ),
            )
        )

        // ==================== QUIVERS ====================
        listItems.addAll(
            listOf(
                // COMMON
                EquipmentName(
                    "Leather Quiver", EnumEquipmentType.QUIVER, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Cloth Quiver", EnumEquipmentType.QUIVER, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Simple Quiver", EnumEquipmentType.QUIVER, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Hide Quiver", EnumEquipmentType.QUIVER, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Light Quiver", EnumEquipmentType.QUIVER, EnumRarity.COMMON
                ),
                // UNCOMMON
                EquipmentName(
                    "Marksman's Quiver", EnumEquipmentType.QUIVER, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Archer's Quiver", EnumEquipmentType.QUIVER, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Warden's Quiver", EnumEquipmentType.QUIVER, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Hunter's Quiver", EnumEquipmentType.QUIVER, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Scout's Quiver", EnumEquipmentType.QUIVER, EnumRarity.UNCOMMON
                ),
                // RARE
                EquipmentName(
                    "Quiver of Precision", EnumEquipmentType.QUIVER, EnumRarity.RARE
                ),
                EquipmentName(
                    "Quiver of Accuracy", EnumEquipmentType.QUIVER, EnumRarity.RARE
                ),
                EquipmentName(
                    "Champion's Quiver", EnumEquipmentType.QUIVER, EnumRarity.RARE
                ),
                EquipmentName(
                    "Quiver of Marksmanship", EnumEquipmentType.QUIVER, EnumRarity.RARE
                ),
                EquipmentName(
                    "Quiver of Swiftness", EnumEquipmentType.QUIVER, EnumRarity.RARE
                ),
                // EPIC
                EquipmentName(
                    "Quiver of Valor", EnumEquipmentType.QUIVER, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Quiver of Justice", EnumEquipmentType.QUIVER, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Champion's Quiver", EnumEquipmentType.QUIVER, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Radiant Quiver", EnumEquipmentType.QUIVER, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Quiver of Enlightenment", EnumEquipmentType.QUIVER, EnumRarity.EPIC
                ),
                // LEGENDARY
                EquipmentName(
                    "Azure Quiver", EnumEquipmentType.QUIVER, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Titan's Quiver", EnumEquipmentType.QUIVER, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Dragonlord's Quiver", EnumEquipmentType.QUIVER, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Quiver of Immortality", EnumEquipmentType.QUIVER, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Legendary Quiver", EnumEquipmentType.QUIVER, EnumRarity.LEGENDARY
                ),
                // MYTHICAL
                EquipmentName(
                    "Quiver of Eternity", EnumEquipmentType.QUIVER, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Divine Quiver", EnumEquipmentType.QUIVER, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Creator's Quiver", EnumEquipmentType.QUIVER, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Quiver of the Immortal", EnumEquipmentType.QUIVER, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Quiver of Destiny", EnumEquipmentType.QUIVER, EnumRarity.MYTHICAL
                ),
            )
        )

        // ==================== SHIELDS ====================
        listItems.addAll(
            listOf(
                // COMMON
                EquipmentName(
                    "Wooden Shield", EnumEquipmentType.SHIELD, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Leather Shield", EnumEquipmentType.SHIELD, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Simple Shield", EnumEquipmentType.SHIELD, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Wicker Shield", EnumEquipmentType.SHIELD, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Hide Shield", EnumEquipmentType.SHIELD, EnumRarity.COMMON
                ),
                // UNCOMMON
                EquipmentName(
                    "Steel Shield", EnumEquipmentType.SHIELD, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Bronze Shield", EnumEquipmentType.SHIELD, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Warden's Shield", EnumEquipmentType.SHIELD, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Forged Shield", EnumEquipmentType.SHIELD, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Knight's Targe", EnumEquipmentType.SHIELD, EnumRarity.UNCOMMON
                ),
                // RARE
                EquipmentName(
                    "Warbringer Shield", EnumEquipmentType.SHIELD, EnumRarity.RARE
                ),
                EquipmentName(
                    "Aegis Shield", EnumEquipmentType.SHIELD, EnumRarity.RARE
                ),
                EquipmentName(
                    "Champion's Shield", EnumEquipmentType.SHIELD, EnumRarity.RARE
                ),
                EquipmentName(
                    "Shield of Fury", EnumEquipmentType.SHIELD, EnumRarity.RARE
                ),
                EquipmentName(
                    "Shield of Fortitude", EnumEquipmentType.SHIELD, EnumRarity.RARE
                ),
                // EPIC
                EquipmentName(
                    "Shield of Justice", EnumEquipmentType.SHIELD, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Shield of Glory", EnumEquipmentType.SHIELD, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Martyr's Shield", EnumEquipmentType.SHIELD, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Radiant Shield", EnumEquipmentType.SHIELD, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Shield of Enlightenment", EnumEquipmentType.SHIELD, EnumRarity.EPIC
                ),
                // LEGENDARY
                EquipmentName(
                    "Azure Shield", EnumEquipmentType.SHIELD, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Titan's Shield", EnumEquipmentType.SHIELD, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Dragonlord's Shield", EnumEquipmentType.SHIELD, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Shield of Immortality", EnumEquipmentType.SHIELD, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Legendary Targe", EnumEquipmentType.SHIELD, EnumRarity.LEGENDARY
                ),
                // MYTHICAL
                EquipmentName(
                    "Shield of Eternity", EnumEquipmentType.SHIELD, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Divine Shield", EnumEquipmentType.SHIELD, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Creator's Shield", EnumEquipmentType.SHIELD, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Shield of the Immortal", EnumEquipmentType.SHIELD, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Shield of Destiny", EnumEquipmentType.SHIELD, EnumRarity.MYTHICAL
                ),
            )
        )

        // ==================== AMULETS ====================
        listItems.addAll(
            listOf(
                // COMMON
                EquipmentName(
                    "Simple Amulet", EnumEquipmentType.AMULET, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Wooden Amulet", EnumEquipmentType.AMULET, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Clay Pendant", EnumEquipmentType.AMULET, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Bone Amulet", EnumEquipmentType.AMULET, EnumRarity.COMMON
                ),
                EquipmentName(
                    "Horn Talisman", EnumEquipmentType.AMULET, EnumRarity.COMMON
                ),
                // UNCOMMON
                EquipmentName(
                    "Copper Amulet", EnumEquipmentType.AMULET, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Silver Pendant", EnumEquipmentType.AMULET, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Bronze Amulet", EnumEquipmentType.AMULET, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Agate Talisman", EnumEquipmentType.AMULET, EnumRarity.UNCOMMON
                ),
                EquipmentName(
                    "Golden Amulet", EnumEquipmentType.AMULET, EnumRarity.UNCOMMON
                ),
                // RARE
                EquipmentName(
                    "Amulet of Might", EnumEquipmentType.AMULET, EnumRarity.RARE
                ),
                EquipmentName(
                    "Amulet of Wisdom", EnumEquipmentType.AMULET, EnumRarity.RARE
                ),
                EquipmentName(
                    "Amulet of Protection", EnumEquipmentType.AMULET, EnumRarity.RARE
                ),
                EquipmentName(
                    "Amulet of Endurance", EnumEquipmentType.AMULET, EnumRarity.RARE
                ),
                EquipmentName(
                    "Amulet of Agility", EnumEquipmentType.AMULET, EnumRarity.RARE
                ),
                // EPIC
                EquipmentName(
                    "Amulet of Valor", EnumEquipmentType.AMULET, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Amulet of Justice", EnumEquipmentType.AMULET, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Champion's Pendant", EnumEquipmentType.AMULET, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Amulet of Radiance", EnumEquipmentType.AMULET, EnumRarity.EPIC
                ),
                EquipmentName(
                    "Amulet of Fate", EnumEquipmentType.AMULET, EnumRarity.EPIC
                ),
                // LEGENDARY
                EquipmentName(
                    "Azure Amulet", EnumEquipmentType.AMULET, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Amulet of the Titan", EnumEquipmentType.AMULET, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Dragonlord's Pendant", EnumEquipmentType.AMULET, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Amulet of Immortality", EnumEquipmentType.AMULET, EnumRarity.LEGENDARY
                ),
                EquipmentName(
                    "Legendary Amulet", EnumEquipmentType.AMULET, EnumRarity.LEGENDARY
                ),
                // MYTHICAL
                EquipmentName(
                    "Amulet of Eternity", EnumEquipmentType.AMULET, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Divine Pendant", EnumEquipmentType.AMULET, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Creator's Amulet", EnumEquipmentType.AMULET, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Amulet of the Immortal", EnumEquipmentType.AMULET, EnumRarity.MYTHICAL
                ),
                EquipmentName(
                    "Amulet of Destiny", EnumEquipmentType.AMULET, EnumRarity.MYTHICAL
                ),
            )
        )

        equipmentNameRepository.insertMany(listItems, session)

        printLog("  → ${listItems.size} EquipmentName created")
    }
}
package features.logic

import application.enums.EnumEquipmentType
import application.enums.EnumEquipmentWeapon
import application.enums.EnumRarity
import application.enums.EnumStatType
import extensions.RandomExt
import features.data.character.Character
import features.data.character.ModificationValue
import features.data.equipment.Accessory
import features.data.equipment.Armor
import features.data.equipment.Equipment
import features.data.equipment.Weapon
import features.caches.EquipmentNameCache
import features.caches.PropertyCache
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.random.Random

class EquipmentGenerator : KoinComponent {
    private val equipmentNameCache: EquipmentNameCache by inject()
    private val propertyCache: PropertyCache by inject()

    // Базовые цены в зависимости от редкости
    private val basePrices = mapOf(
        EnumRarity.COMMON to 10L,
        EnumRarity.UNCOMMON to 50L,
        EnumRarity.RARE to 250L,
        EnumRarity.EPIC to 1000L,
        EnumRarity.LEGENDARY to 10000L,
        EnumRarity.MYTHICAL to 200000L,
    )

    // Множители цены в зависимости от уровня и улучшения
    private val levelPriceMultiplier = 1.5
    private val enhancePriceMultiplier = 2.0

    // Множители для характеристик оружия
    private val baseDamageValues = mapOf(
        EnumRarity.COMMON to 10,
        EnumRarity.UNCOMMON to 20,
        EnumRarity.RARE to 40,
        EnumRarity.EPIC to 80,
        EnumRarity.LEGENDARY to 150,
        EnumRarity.MYTHICAL to 300,
    )

    private val baseDefenseValues = mapOf(
        EnumRarity.COMMON to 5,
        EnumRarity.UNCOMMON to 10,
        EnumRarity.RARE to 20,
        EnumRarity.EPIC to 40,
        EnumRarity.LEGENDARY to 75,
        EnumRarity.MYTHICAL to 150,
    )

    fun generateEquipment(character: Character?): Equipment {
        val slot = randomizeSlot()
        val rarity = randomizeRarity()
        val itemLevel = randomizeItemLevel(character?.level ?: 1)
        val enhanceLevel = randomizeEnhanceLevel(rarity)
        val name = randomizeName(slot, rarity)
        val price = calculatePrice(rarity, itemLevel, enhanceLevel)
        val description = generateDescription(slot, rarity)

        return when (slot) {
            EnumEquipmentType.WEAPON_1H, EnumEquipmentType.WEAPON_2H -> {
                val weaponType = randomizeWeaponType(slot)!!
                val damage = calculateDamage(rarity, itemLevel, weaponType)
                val attackSpeed = calculateAttackSpeed(weaponType)
                val durability = calculateDurability(rarity, itemLevel)

                Weapon(
                    slot = slot,
                    weaponType = weaponType,
                    damage = damage,
                    attackSpeed = attackSpeed,
                    name = name,
                    rarity = rarity,
                    itemLevel = itemLevel,
                    enhanceLevel = enhanceLevel,
                    price = price,
                    description = description,
                    durability = durability
                ).apply {
                    params = generateModifiers(rarity, itemLevel)
                }
            }

            EnumEquipmentType.HELMET, EnumEquipmentType.BODY, EnumEquipmentType.GLOVES,
            EnumEquipmentType.BOOTS, EnumEquipmentType.SHIELD -> {
                val defense = calculateDefense(rarity, itemLevel)

                Armor(
                    slot = slot,
                    defense = defense,
                    name = name,
                    rarity = rarity,
                    itemLevel = itemLevel,
                    enhanceLevel = enhanceLevel,
                    price = price,
                    description = description
                ).apply {
                    params = generateModifiers(rarity, itemLevel)
                }
            }

            else -> {
                Accessory(
                    slot = slot,
                    name = name,
                    rarity = rarity,
                    itemLevel = itemLevel,
                    enhanceLevel = enhanceLevel,
                    price = price,
                    description = description
                ).apply {
                    params = generateModifiers(rarity, itemLevel)
                }
            }
        }
    }

    private fun randomizeSlot(): EnumEquipmentType {
        return EnumEquipmentType.entries.random(RandomExt.random)
    }

    private fun randomizeRarity(): EnumRarity {
        val rarities = listOf(
            EnumRarity.COMMON to 60.0,
            EnumRarity.UNCOMMON to 20.0,
            EnumRarity.RARE to 9.0,
            EnumRarity.EPIC to 3.0,
            EnumRarity.LEGENDARY to 0.5,
            EnumRarity.MYTHICAL to 0.001
        )

        val totalWeight = rarities.sumOf { it.second }
        var random = Random.nextDouble(totalWeight)

        for ((rarity, weight) in rarities) {
            random -= weight
            if (random <= 0) {
                return rarity
            }
        }

        return EnumRarity.COMMON
    }

    private fun randomizeItemLevel(characterLevel: Short): Int {
        // Предмет может быть от уровня персонажа - 3 до уровня персонажа + 2
        val minLevel = maxOf(1, characterLevel - 3)
        val maxLevel = characterLevel + 2
        return Random.nextInt(minLevel, maxLevel + 1)
    }

    private fun randomizeEnhanceLevel(rarity: EnumRarity): Int {
        // Шанс на улучшение зависит от редкости
        val maxEnhance = when (rarity) {
            EnumRarity.COMMON -> 0
            EnumRarity.UNCOMMON -> Random.nextInt(0, 3)
            EnumRarity.RARE -> Random.nextInt(0, 5)
            EnumRarity.EPIC -> Random.nextInt(0, 8)
            EnumRarity.LEGENDARY -> Random.nextInt(0, 12)
            EnumRarity.MYTHICAL -> Random.nextInt(0, 16)
        }

        return maxEnhance
    }

    private fun randomizeName(slot: EnumEquipmentType, rarity: EnumRarity): String {
        return equipmentNameCache.getCache()
            .filter { it.rarity == rarity && it.type == slot }.shuffled(RandomExt.random)
            .first().name
    }

    private fun randomizeWeaponType(slot: EnumEquipmentType): EnumEquipmentWeapon? {
        if (slot == EnumEquipmentType.WEAPON_1H) return EnumEquipmentWeapon.entries.filter { !it.twoHanded }
            .random(RandomExt.random)
        if (slot == EnumEquipmentType.WEAPON_2H) return EnumEquipmentWeapon.entries.filter { it.twoHanded }
            .random(RandomExt.random)

        return null
    }

    private fun calculateDamage(
        rarity: EnumRarity,
        itemLevel: Int,
        weaponType: EnumEquipmentWeapon
    ): Int {
        val baseDamage = baseDamageValues[rarity] ?: 10
        val weaponMultiplier = when (weaponType) {
            EnumEquipmentWeapon.SWORD -> 1.0
            EnumEquipmentWeapon.LONGSWORD -> 1.2
            EnumEquipmentWeapon.BOW -> 1.1
            EnumEquipmentWeapon.WAND -> 1.3
            EnumEquipmentWeapon.AXE -> 1.15
            EnumEquipmentWeapon.DOUBLEAXE -> 1.25
            EnumEquipmentWeapon.DOUBLESWORD -> 1.25
            EnumEquipmentWeapon.BLADE -> 1.05
        }

        // Урон растет с уровнем
        val levelMultiplier = 1 + (itemLevel - 1) * 0.1

        return (baseDamage * weaponMultiplier * levelMultiplier).toInt()
    }

    private fun calculateAttackSpeed(weaponType: EnumEquipmentWeapon): Double {
        // Базовая скорость для разных типов оружия
        return when (weaponType) {
            EnumEquipmentWeapon.SWORD -> 1.0
            EnumEquipmentWeapon.LONGSWORD -> 0.8
            EnumEquipmentWeapon.BOW -> 0.9
            EnumEquipmentWeapon.WAND -> 1.2
            EnumEquipmentWeapon.AXE -> 0.7
            EnumEquipmentWeapon.DOUBLEAXE -> 0.6
            EnumEquipmentWeapon.DOUBLESWORD -> 0.75
            EnumEquipmentWeapon.BLADE -> 1.1
        }
    }

    private fun calculateDefense(rarity: EnumRarity, itemLevel: Int): Int {
        val baseDefense = baseDefenseValues[rarity] ?: 5
        val levelMultiplier = 1 + (itemLevel - 1) * 0.1

        return (baseDefense * levelMultiplier).toInt()
    }

    private fun calculateDurability(rarity: EnumRarity, itemLevel: Int): Int {
        val baseDurability = when (rarity) {
            EnumRarity.COMMON -> 100
            EnumRarity.UNCOMMON -> 150
            EnumRarity.RARE -> 200
            EnumRarity.EPIC -> 300
            EnumRarity.LEGENDARY -> 500
            EnumRarity.MYTHICAL -> 1000
        }

        val levelMultiplier = 1 + (itemLevel - 1) * 0.1
        val enhanceMultiplier = 1 + itemLevel * 0.05

        return (baseDurability * levelMultiplier * enhanceMultiplier).toInt()
    }

    private fun calculateWeight(slot: EnumEquipmentType, rarity: EnumRarity): Int {
        val baseWeight = when (slot) {
            EnumEquipmentType.HELMET -> 5
            EnumEquipmentType.BODY -> 20
            EnumEquipmentType.GLOVES -> 3
            EnumEquipmentType.BOOTS -> 5
            EnumEquipmentType.SHIELD -> 15
            else -> 2
        }

        val rarityMultiplier = when (rarity) {
            EnumRarity.COMMON -> 1.0
            EnumRarity.UNCOMMON -> 1.1
            EnumRarity.RARE -> 1.2
            EnumRarity.EPIC -> 1.3
            EnumRarity.LEGENDARY -> 1.5
            EnumRarity.MYTHICAL -> 1.8
        }

        return (baseWeight * rarityMultiplier).toInt()
    }

    private fun calculatePrice(rarity: EnumRarity, itemLevel: Int, enhanceLevel: Int): Long {
        val basePrice = basePrices[rarity] ?: 10L

        // Цена растет с уровнем и улучшением
        val levelMultiplier = (1 + (itemLevel - 1) * 0.2)
        val enhanceMultiplier = (1 + enhanceLevel * 0.5)

        return (basePrice * levelMultiplier * enhanceMultiplier).toLong()
    }

    private fun generateDescription(slot: EnumEquipmentType, rarity: EnumRarity): String {
        val rarityText = rarity.text
        return rarityText
    }

    private fun generateModifiers(
        rarity: EnumRarity,
        itemLevel: Int
    ): ArrayList<ModificationValue> {
        val stock = ArrayList<ModificationValue>()

        val countModifiers = when (rarity) {
            EnumRarity.COMMON -> 0..1
            EnumRarity.UNCOMMON -> 1..2
            EnumRarity.RARE -> 2..3
            EnumRarity.EPIC -> 2..4
            EnumRarity.LEGENDARY -> 2..5
            EnumRarity.MYTHICAL -> 3..6
        }.random(RandomExt.random)

        val cachedProperties = propertyCache.getCache()
            .filter { it.type == EnumStatType.STOCK }

        for (i in 1..countModifiers) {
            val property = cachedProperties.random(RandomExt.random)
            stock.add(ModificationValue(property.getId(), itemLevel.toByte(), 100.0))
        }

        return stock
    }

    fun generateMultipleEquipment(
        character: Character?,
        count: Int
    ): List<Equipment> {
        return (1..count).map { generateEquipment(character) }
    }
}
package features.logic

import application.enums.EnumEquipmentType
import application.enums.EnumRarity
import features.data.character.Character
import features.data.equipment.Equipment
import org.bson.types.ObjectId
import kotlin.random.Random

class ObjectGenerator {

    // Словари для генерации названий
    private val prefixes = listOf(
        "Стальной", "Железный", "Мифриловый", "Адамантовый",
        "Золотой", "Серебряный", "Бронзовый", "Титановый",
        "Драконий", "Эльфийский", "Дварфийский", "Орочий",
        "Зачарованный", "Древний", "Легендарный", "Мистический",
        "Тёмный", "Светлый", "Кровавый", "Ледяной",
        "Огненный", "Ветреный", "Земляной", "Водяной"
    )

    private val suffixes = listOf(
        "Защитника", "Воителя", "Стражника", "Берсерка",
        "Мага", "Теней", "Рассвета", "Заката",
        "Грома", "Молний", "Мрака", "Света",
        "Крови", "Битвы", "Победителя", "Героя",
        "Судьбы", "Власти", "Мудрости", "Отваги"
    )

    private val slotBaseNames = mapOf(
        EnumEquipmentType.HELMET to "Шлем",
        EnumEquipmentType.BODY to "Кираса",
        EnumEquipmentType.GLOVES to "Перчатки",
        EnumEquipmentType.RING to "Кольцо",
        EnumEquipmentType.BOOTS to "Сапоги"
    )

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

    fun generateEquipment(character: Character): Equipment {
        val slot = randomizeSlot()
        val rarity = randomizeRarity()
        val itemLevel = randomizeItemLevel(character.level)
        val enhanceLevel = randomizeEnhanceLevel(rarity)
        val name = randomizeName(slot, rarity)
        val price = calculatePrice(rarity, itemLevel, enhanceLevel)
        val description = generateDescription(slot, rarity)

        return Equipment(
            slot = slot,
            name = name,
            rarity = rarity,
            itemLevel = itemLevel,
            enhanceLevel = enhanceLevel,
            price = price,
            description = description,
            _id = ObjectId()
        )
    }

    private fun randomizeSlot(): EnumEquipmentType {
        // Взвешенная случайность для разных типов слотов
        val weights = mapOf(
            EnumEquipmentType.HELMET to 20,
            EnumEquipmentType.BODY to 20,
            EnumEquipmentType.GLOVES to 20,
            EnumEquipmentType.BOOTS to 20,
            EnumEquipmentType.RING to 20
        )

        val totalWeight = weights.values.sum()
        var random = Random.nextInt(totalWeight)

        for ((slot, weight) in weights) {
            random -= weight
            if (random <= 0) {
                return slot
            }
        }

        return EnumEquipmentType.entries.random()
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

    private fun randomizeName(
        slot: EnumEquipmentType,
        rarity: EnumRarity,
    ): String {
        val prefix = prefixes.random()
        val suffix = suffixes.random()
        val baseName = slotBaseNames[slot] ?: "Предмет"

        val rarityPrefix = rarity.text

        return when (Random.nextInt(4)) {
            0 -> "$prefix $baseName $suffix"
            1 -> if (rarityPrefix.isNotEmpty()) "$rarityPrefix $baseName" else "$prefix $baseName"
            2 -> "$suffix $baseName"
            3 -> "$baseName ${if (Random.nextBoolean()) prefix else suffix}"
            else -> "$prefix $baseName"
        }.trim()
    }

    private fun calculatePrice(rarity: EnumRarity, itemLevel: Int, enhanceLevel: Int): Long {
        val basePrice = basePrices[rarity] ?: 10L

        // Цена растет с уровнем и улучшением
        val levelMultiplier = (1 + (itemLevel - 1) * 0.2)
        val enhanceMultiplier = (1 + enhanceLevel * 0.5)

        return (basePrice * levelMultiplier * enhanceMultiplier).toLong()
    }

    private fun generateDescription(
        slot: EnumEquipmentType,
        rarity: EnumRarity,
    ): String {
        val rarityText = rarity.text

        return rarityText
    }

    // Дополнительные методы для массовой генерации

    fun generateEquipmentWithSlot(
        character: Character,
        slot: EnumEquipmentType
    ): Equipment {
        return generateEquipment(character).copy(slot = slot)
    }

    fun generateEquipmentSet(character: Character): List<Equipment> {
        return EnumEquipmentType.entries.map { slot ->
            generateEquipment(character).copy(slot = slot)
        }
    }

    fun generateMultipleEquipment(
        character: Character,
        count: Int
    ): List<Equipment> {
        return (1..count).map { generateEquipment(character) }
    }

    fun generateEquipmentWithRarity(
        character: Character,
        rarity: EnumRarity
    ): Equipment {
        return generateEquipment(character).copy(rarity = rarity)
    }

    fun generateUpgradedEquipment(
        character: Character,
        baseEquipment: Equipment
    ): Equipment {
        val newLevel = baseEquipment.itemLevel + Random.nextInt(1, 3)
        val newRarity = if (Random.nextBoolean()) {
            val allRarities = EnumRarity.entries
            val currentIndex = allRarities.indexOf(baseEquipment.rarity)
            if (currentIndex < allRarities.size - 1 && Random.nextInt(100) < 20) {
                allRarities[currentIndex + 1]
            } else {
                baseEquipment.rarity
            }
        } else {
            baseEquipment.rarity
        }

        return baseEquipment.copy(
            itemLevel = newLevel,
            rarity = newRarity,
            price = calculatePrice(newRarity, newLevel, baseEquipment.enhanceLevel),
            description = generateDescription(baseEquipment.slot, newRarity)
        )
    }
}
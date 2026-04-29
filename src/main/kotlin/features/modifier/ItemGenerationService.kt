package features.modifier

import application.enums.EnumEquipmentType
import application.enums.EnumModifierCategory
import application.enums.EnumModifierGroup
import application.enums.EnumRarity
import application.enums.EnumStatModifierHelper
import application.model.StatModifier
import extensions.RandomExt
import features.equipment.Equipment
import features.property.PropertyCache

/**
 * Генератор предметов в стиле PoE.
 *
 * ## Алгоритм генерации:
 * 1. Определить количество имплицитов по редкости
 * 2. Определить количество PREFIX/SUFFIX по редкости
 * 3. Для каждого аффикса: выбрать шаблон (weighted random) → выбрать тир (weighted random) → прокатить значение
 * 4. Собрать название из имён аффиксов
 * 5. Рассчитать цену
 *
 * ## Правила складывания (PoE):
 *   - На одном предмете не может быть двух модов одной [EnumModifierGroup]
 *   - PREFIX отделены от SUFFIX — каждый тип не может превысить лимит своей категории
 */
object ItemGenerationService {

    // Базовые названия слотов
    private val BASE_NAMES = mapOf(
        EnumEquipmentType.HELMET to "Шлем",
        EnumEquipmentType.BODY   to "Доспех",
        EnumEquipmentType.GLOVES to "Перчатки",
        EnumEquipmentType.BOOTS  to "Сапоги",
        EnumEquipmentType.RING   to "Кольцо"
    )

    // Имена для RARE/EPIC предметов (случайные двухсловные)
    private val RARE_ADJECTIVES = listOf(
        "Сумрачный", "Железный", "Кровавый", "Костяной", "Ледяной",
        "Огненный", "Теневой", "Древний", "Проклятый", "Громовой",
        "Звёздный", "Адский", "Небесный", "Хаотичный", "Призрачный"
    )
    private val RARE_NOUNS = listOf(
        "Рок", "Клинок", "Ужас", "Дар", "Разрушитель",
        "Страж", "Хранитель", "Вихрь", "Пламя", "Призыв",
        "Шёпот", "Предел", "Гнев", "Тайна", "Удар"
    )

    /**
     * Сгенерировать предмет с аффиксами и сохранить в БД.
     *
     * @param slot        Слот экипировки
     * @param itemLevel   Уровень предмета (1..100)
     * @param rarity      Редкость — определяет количество аффиксов
     * @param characterId Владелец
     * @return Готовый объект [Equipment] (ещё НЕ сохранён в БД)
     */
    fun generate(
        slot: EnumEquipmentType,
        itemLevel: Int,
        rarity: EnumRarity,
        characterId: Long
    ): Equipment {
        val implicit = rollImplicits(slot, itemLevel, rarity)
        val modifiers = rollExplicits(slot, itemLevel, rarity)
        val name = buildName(slot, rarity, modifiers)
        val price = calculatePrice(rarity, itemLevel, modifiers)

        return Equipment(
            name = name,
            slot = slot,
            rarity = rarity,
            itemLevel = itemLevel,
            characterId = characterId,
            implicit = implicit.toMutableSet(),
            modifiers = modifiers.toMutableSet(),
            price = price,
            description = buildDescription(implicit, modifiers)
        )
    }

    // ===== IMPLICIT =====

    private fun rollImplicits(
        slot: EnumEquipmentType,
        itemLevel: Int,
        rarity: EnumRarity
    ): List<StatModifier> {
        val pool = ModifierPool.getImplicitPool(slot, itemLevel)
        if (pool.isEmpty()) return emptyList()

        val count = rarity.maxImplicits.coerceAtMost(pool.size)
        val usedGroups = mutableSetOf<EnumModifierGroup>()
        val result = mutableListOf<StatModifier>()

        repeat(count) {
            val available = pool.filter { (t, _) -> t.group !in usedGroups }
            if (available.isEmpty()) return@repeat
            val (template, tier) = weightedPick(available) ?: return@repeat
            usedGroups += template.group
            result += rollModifier(template, tier)
        }
        return result
    }

    // ===== EXPLICIT =====

    private fun rollExplicits(
        slot: EnumEquipmentType,
        itemLevel: Int,
        rarity: EnumRarity
    ): List<StatModifier> {
        if (rarity.maxPrefixes == 0 && rarity.maxSuffixes == 0) return emptyList()

        // Сколько префиксов и суффиксов кинуть
        val (numPrefixes, numSuffixes) = rollAfixCount(rarity)

        val usedGroups = mutableSetOf<EnumModifierGroup>()
        val result = mutableListOf<StatModifier>()

        // Префиксы
        repeat(numPrefixes) {
            val pool = ModifierPool.getExplicitPool(slot, itemLevel, EnumModifierCategory.PREFIX, usedGroups)
            val (template, tier) = weightedPick(pool) ?: return@repeat
            usedGroups += template.group
            result += rollModifier(template, tier)
        }

        // Суффиксы
        repeat(numSuffixes) {
            val pool = ModifierPool.getExplicitPool(slot, itemLevel, EnumModifierCategory.SUFFIX, usedGroups)
            val (template, tier) = weightedPick(pool) ?: return@repeat
            usedGroups += template.group
            result += rollModifier(template, tier)
        }

        return result
    }

    /**
     * Определить количество PREFIX и SUFFIX аффиксов по редкости.
     * Для RARE/EPIC — случайное число в диапазоне [min, max].
     */
    private fun rollAfixCount(rarity: EnumRarity): Pair<Int, Int> = when (rarity) {
        EnumRarity.COMMON     -> 0 to 0
        EnumRarity.UNCOMMON   -> 1 to 1
        EnumRarity.RARE       -> {
            val p = RandomExt.randomInt(1, rarity.maxPrefixes + 1)
            val s = RandomExt.randomInt(1, rarity.maxSuffixes + 1)
            p to s
        }
        EnumRarity.EPIC       -> {
            val p = RandomExt.randomInt(2, rarity.maxPrefixes + 1)
            val s = RandomExt.randomInt(2, rarity.maxSuffixes + 1)
            p to s
        }
        EnumRarity.LEGENDARY  -> rarity.maxPrefixes to rarity.maxSuffixes
    }

    // ===== ROLL SINGLE MODIFIER =====

    /**
     * Из шаблона и тира прокатить конкретное значение.
     * Значение — случайное в диапазоне [tier.minValue, tier.maxValue].
     */
    private fun rollModifier(template: ModifierTemplate, tier: ModifierTierDef): StatModifier {
        val rawValue = RandomExt.randomDouble(tier.minValue, tier.maxValue)
        val value = Math.round(rawValue * 10) / 10.0  // 1 знак после запятой

        val statId = PropertyCache.getIdFromEnum(template.stat)
        return StatModifier(
            statId = statId,
            modType = template.modType,
            category = template.category,
            group = template.group,
            tier = tier.tier,
            value = value,
            minValue = tier.minValue,
            maxValue = tier.maxValue,
            tags = template.tags
        )
    }

    // ===== WEIGHTED PICK =====

    /**
     * Взвешенный случайный выбор из пула (template, tierDef).
     * Вес определяется [ModifierTierDef.weight] × [ModifierTemplate.weight] / 1000.
     */
    private fun weightedPick(
        pool: List<Pair<ModifierTemplate, ModifierTierDef>>
    ): Pair<ModifierTemplate, ModifierTierDef>? {
        if (pool.isEmpty()) return null
        val totalWeight = pool.sumOf { (t, tier) -> (t.weight.toLong() * tier.weight / 1000).coerceAtLeast(1) }
        var roll = RandomExt.randomLong(0, totalWeight)
        for ((template, tier) in pool) {
            roll -= (template.weight.toLong() * tier.weight / 1000).coerceAtLeast(1)
            if (roll <= 0) return template to tier
        }
        return pool.last()
    }

    // ===== NAME GENERATION =====

    private fun buildName(
        slot: EnumEquipmentType,
        rarity: EnumRarity,
        modifiers: List<StatModifier>
    ): String {
        val base = BASE_NAMES[slot] ?: slot.name.lowercase().replaceFirstChar { it.uppercase() }
        return when (rarity) {
            EnumRarity.COMMON -> base

            EnumRarity.UNCOMMON -> {
                val prefix = modifiers.firstOrNull { it.category == EnumModifierCategory.PREFIX }
                    ?.let { mod -> getModifierDisplayName(mod) } ?: ""
                val suffix = modifiers.firstOrNull { it.category == EnumModifierCategory.SUFFIX }
                    ?.let { mod -> "«${getModifierDisplayName(mod)}»" } ?: ""
                listOf(prefix, base, suffix).filter { it.isNotBlank() }.joinToString(" ")
            }

            EnumRarity.RARE, EnumRarity.EPIC -> {
                val adj = RARE_ADJECTIVES[RandomExt.randomInt(0, RARE_ADJECTIVES.size)]
                val noun = RARE_NOUNS[RandomExt.randomInt(0, RARE_NOUNS.size)]
                "$adj $noun"
            }

            EnumRarity.LEGENDARY -> {
                val adj = RARE_ADJECTIVES[RandomExt.randomInt(0, RARE_ADJECTIVES.size)]
                "Легендарный $adj $base"
            }
        }
    }

    private fun getModifierDisplayName(mod: StatModifier): String {
        return EnumStatModifierHelper.entries
            .find { it.group == mod.group }
            ?.displayName ?: ""
    }

    // ===== PRICE =====

    private fun calculatePrice(
        rarity: EnumRarity,
        itemLevel: Int,
        modifiers: List<StatModifier>
    ): ULong {
        val rarityMult = when (rarity) {
            EnumRarity.COMMON    -> 1.0
            EnumRarity.UNCOMMON  -> 3.0
            EnumRarity.RARE      -> 10.0
            EnumRarity.EPIC      -> 30.0
            EnumRarity.LEGENDARY -> 100.0
        }
        val modBonus = modifiers.sumOf { m ->
            // Чем лучше тир (меньший номер), тем ценнее мод
            ((8 - m.tier + 1) * 50).toLong()
        }
        val base = (100L + itemLevel * 20L + modBonus)
        return (base * rarityMult).toLong().toULong().coerceAtLeast(10u)
    }

    // ===== DESCRIPTION =====

    private fun buildDescription(
        implicit: List<StatModifier>,
        modifiers: List<StatModifier>
    ): String {
        val parts = mutableListOf<String>()
        if (implicit.isNotEmpty()) {
            parts += implicit.joinToString("; ") { m ->
                val name = PropertyCache.getAll().find { it.id == m.statId }?.name ?: "?"
                m.displayString(name)
            }
        }
        if (modifiers.isNotEmpty()) {
            parts += modifiers.joinToString("; ") { m ->
                val name = PropertyCache.getAll().find { it.id == m.statId }?.name ?: "?"
                m.displayString(name)
            }
        }
        return parts.joinToString(" | ")
    }
}

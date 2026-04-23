package ru.descend

import application.enums.EnumStatHelper
import extensions.RandomExt
import extensions.printLog
import extensions.randomExt
import kotlin.test.Test

enum class EnumStatModifierHelper(val mod: EnumStatHelper, val stockFlat: Double?, val stockPercent: Double?, val requiredLevel: Long) {
    MOD_HEALTH(EnumStatHelper.STOCK_HEALTH, 15.0, 0.02, 1),
    MOD_MANA(EnumStatHelper.STOCK_MANA, 10.0, 0.03, 1),
    MOD_ATTACK_SPEED(EnumStatHelper.STOCK_ATTACK_SPEED, 5.0, 0.025, 5),
}

class StatTest {

    @Test
    fun testStats() {

        val mod = EnumStatModifierHelper.MOD_HEALTH
        printLog("*****")

        // Создаём персонажа с базовым физическим уроном 10
        val physDmg = CharacterStat(EnumStatHelper.STOCK_ATTACK_PHYSICAL, baseValue = 10.0)

        // Добавляем префикс "Кровавый" (увеличение физ. урона)
        val bloodMod = StatModifier(
            stat = EnumStatHelper.STOCK_ATTACK_PHYSICAL,
            type = ModifierType.INCREASED,
            value = 50.0,
            category = ModifierCategory.PREFIX,
            tier = ModifierTier.TIER_3,
            name = "Кровавый Мастерский"
        )
        physDmg.addModifier(bloodMod)

        println(physDmg.getDescription())
        // Вывод: База: 10.0
        //        PREFIX: [Мастерский] Префикс +50% увеличение к Физический урон
        //        Итог: 15.0

        repeat(10) {
            val randLevel = RandomExt.randomInt(1..30)
            printLog(RandomStatGenerator.generateRandomModifier(randLevel, ModifierCategory.entries.randomExt())?.displayString())
        }

        repeat(100) {
            printLog("RAND: ${RandomExt.randomInt(0..100)}")
        }
    }
}

// ================================ БАЗОВЫЕ ТИПЫ ================================

enum class StatType {
    FLAT,           // Плоское значение (+5 к здоровью)
    PERCENT,        // Процентное (10% увеличение урона)
    ADDITIVE,       // Аддитивный множитель (Increased)
    MULTIPLICATIVE, // Мультипликативный (More)
    RATIO,          // Соотношение (1% здоровья регенится в секунду)
    BOOLEAN,        // Флаг (жив, отравлен)
    RESIST,         // Сопротивление
    PENETRATION,    // Проникновение
    LEECH           // Вампиризм
}

enum class ModifierCategory {
    PREFIX,         // Префикс (название перед свойством)
    SUFFIX,         // Суффикс (название после свойства)
    IMPLICIT,       // Скрытое свойство (от базового предмета)
    CRAFTED,        // Высшая/созданное свойство
    ENCHANT,        // Зачарование (лабиринт)
    CORRUPTED,      // Испорченное свойство
    UNIQUE,         // Уникальный эффект
    SET_BONUS,      // Бонус комплекта
    PASSIVE_SKILL,  // Умение с дерева пассивок
    ASCENDANCY,     // Аванпост (авенданси класс)
    JEWEL,          // Самоцвет
    FLASK_EFFECT,   // Эффект флакона
    TEMPORARY_BUFF, // Временный бафф
    CURSE,          // Проклятие на враге
    AURA            // Аура
}

enum class ModifierTier(val tier: Int, val names: String, val color: String) {
    TIER_1(1, "Зеркальный", "#FFD700"),
    TIER_2(2, "Легендарный", "#FF8000"),
    TIER_3(3, "Мастерский", "#B84CFF"),
    TIER_4(4, "Редкий", "#FF4444"),
    TIER_5(5, "Продвинутый", "#44FF44"),
    TIER_6(6, "Обычный", "#FFFFFF"),
    TIER_7(7, "Низкий", "#888888"),
    TIER_8(8, "Бедный", "#666666")
}

// ================================ ТИПЫ МОДИФИКАТОРОВ (ПРАВИЛА СКЛАДЫВАНИЯ) ================================

enum class ModifierType(
    val stacking: String,               // Как складывается
    val formula: (base: Double, add: Double) -> Double
) {
    FLAT_ADD("Складывается с плоскими бонусами", { base, add -> base + add }),
    INCREASED("Суммируется с другими increased", { base, inc -> base * (1 + inc / 100) }),
    MORE("Перемножается с другими more", { base, more -> base * (1 + more / 100) }),
    EXTRA_AS("Дополнительный урон как", { base, extra -> base + base * (extra / 100) }),
    PENETRATION("Проникновение (снижает сопротивление)", { base, pen -> base + pen }),
    REDUCTION("Уменьшение (отнимается от increased)", { base, red -> base * (1 - red / 100) }),
    LESS("Меньше (делит итоговое значение)", { base, less -> base * (1 - less / 100) });

    companion object {
        fun calculateFinal(baseValue: Double, modifiers: List<Pair<ModifierType, Double>>): Double {
            var result = baseValue
            val flat = modifiers.filter { it.first == FLAT_ADD }.sumOf { it.second }
            val increased = modifiers.filter { it.first == INCREASED }.sumOf { it.second }
            val more = modifiers.filter { it.first == MORE }.sumOf { it.second }
            val extra = modifiers.filter { it.first == EXTRA_AS }.sumOf { it.second }
            val reduction = modifiers.filter { it.first == REDUCTION }.sumOf { it.second }
            val less = modifiers.filter { it.first == LESS }.sumOf { it.second }

            // Сначала плоские
            result += flat
            // Increased / Reduction
            result *= (1 + (increased - reduction) / 100)
            // More / Less перемножаются
            result *= (1 + more / 100)
            result *= (1 - less / 100)
            // Extra as добавляется сверху
            result += result * (extra / 100)

            return result
        }
    }
}

// ================================ УНИКАЛЬНЫЕ ЭФФЕКТЫ ================================

enum class UniqueEffectType {
    ON_HIT,                 // При попадании
    ON_CRIT,                // При критическом ударе
    ON_KILL,                // При убийстве
    ON_BLOCK,               // При блоке
    ON_DODGE,               // При уклонении
    ON_TAKEN_HIT,           // При получении удара
    ON_FLASK_USE,           // При использовании флакона
    ON_LEVEL_UP,            // При повышении уровня
    ON_STATUS_AILMENT,      // При наложении статуса
    PERMANENT,              // Постоянный эффект
    TIMED                   // Временный эффект
}

// ================================ СВОЙСТВА ЭФФЕКТОВ ================================

enum class EffectPropertyType {
    DURATION,           // Длительность эффекта (сек)
    RADIUS,             // Радиус действия (юниты)
    STACKS,             // Количество стаков
    DAMAGE_MULTIPLIER,  // Множитель урона
    CHANCE_TO_APPLY,    // Шанс наложения
    COOLDOWN,           // Перезарядка (сек)
    MANA_COST,          // Стоимость маны
    LIFE_COST,          // Стоимость здоровья
    TRIGGER_RATE        // Частота срабатывания (раз в сек)
}

data class EffectProperty(
    val type: EffectPropertyType,
    val baseValue: Double,
    val perLevel: Double = 0.0,
    val perQuality: Double = 0.0,
    val scalingStat: EnumStatHelper? = null
) {
    fun getValue(level: Int = 1, quality: Int = 0): Double {
        var value = baseValue + perLevel * (level - 1)
        value += baseValue * (quality / 100.0) * perQuality
        return value
    }
}

// ================================ ОДИН МОДИФИКАТОР ХАРАКТЕРИСТИКИ ================================

data class StatModifier(
    val stat: EnumStatHelper,
    val type: ModifierType,
    val value: Double,                  // Может быть отрицательным
    val category: ModifierCategory,
    val tier: ModifierTier? = null,
    val name: String? = null,           // Имя модификатора (для отображения)
    val effectProperties: List<EffectProperty> = emptyList(),
    val requires: List<EnumStatHelper> = emptyList(),  // Требования к другим статам
    val conflictsWith: List<ModifierCategory> = emptyList(), // С чем не суммируется
    val isLocal: Boolean = false,       // Локальный модификатор (только для этого предмета)
    val isGlobal: Boolean = true,       // Глобальный
    val tag: String? = null             // Тег для группировки (например "fire", "attack", "spell")
) {
    fun displayString(): String {
        val prefix = when (type) {
            ModifierType.FLAT_ADD -> "+$value"
            ModifierType.INCREASED -> "${value}% увеличение"
            ModifierType.MORE -> "${value}% больше"
            ModifierType.EXTRA_AS -> "${value}% дополнительного урона как"
            ModifierType.PENETRATION -> "проникновение $value%"
            ModifierType.REDUCTION -> "${value}% уменьшение"
            ModifierType.LESS -> "${value}% меньше"
        }
        val tierStr = tier?.let { "[${it.name}] " } ?: ""
        val catStr = when (category) {
            ModifierCategory.PREFIX -> "Префикс "
            ModifierCategory.SUFFIX -> "Суффикс "
            else -> ""
        }
        return "$tierStr$catStr$prefix к ${stat.nameRu}"
    }
}

// ================================ ГЕНЕРАТОР СЛУЧАЙНЫХ МОДИФИКАТОРОВ (ПРЕФИКСЫ/СУФФИКСЫ) ================================

data class ModifierTemplate(
    val name: String,
    val stat: EnumStatHelper,
    val type: ModifierType,
    val category: ModifierCategory,
    val tierRange: IntRange,            // От 1 до 8
    val valueMin: Double,
    val valueMax: Double,
    val tags: List<String> = emptyList(),
    val requiredItemLevel: Int = 1,
    val weight: Int = 1000              // Вес для случайного выбора
)

object RandomStatGenerator {
    private val templates = mutableListOf<ModifierTemplate>()

    init {
        // Префиксы (урон)
        templates.addAll(listOf(
            ModifierTemplate("Кровавый", EnumStatHelper.STOCK_ATTACK_PHYSICAL, ModifierType.INCREASED, ModifierCategory.PREFIX, 1..8, 10.0, 80.0, listOf("physical", "attack"), 1, 1000),
            ModifierTemplate("Пылающий", EnumStatHelper.STOCK_ATTACK_FIRE, ModifierType.INCREASED, ModifierCategory.PREFIX, 2..7, 15.0, 60.0, listOf("fire", "spell"), 10, 800),
            ModifierTemplate("Леденящий", EnumStatHelper.STOCK_ATTACK_COLD, ModifierType.INCREASED, ModifierCategory.PREFIX, 2..7, 15.0, 60.0, listOf("cold", "spell"), 10, 800),
            ModifierTemplate("Громовой", EnumStatHelper.STOCK_ATTACK_LIGHTNING, ModifierType.INCREASED, ModifierCategory.PREFIX, 2..7, 15.0, 60.0, listOf("lightning", "spell"), 10, 800),
            ModifierTemplate("Хаотичный", EnumStatHelper.STOCK_ATTACK_CHAOS, ModifierType.INCREASED, ModifierCategory.PREFIX, 3..8, 10.0, 50.0, listOf("chaos", "spell"), 20, 600)
        ))

        // Суффиксы (скорость, крит)
        templates.addAll(listOf(
            ModifierTemplate("Проворства", EnumStatHelper.STOCK_ATTACK_SPEED, ModifierType.INCREASED, ModifierCategory.SUFFIX, 1..8, 5.0, 30.0, listOf("attack", "speed"), 1, 900),
            ModifierTemplate("Колдовства", EnumStatHelper.STOCK_CAST_SPEED, ModifierType.INCREASED, ModifierCategory.SUFFIX, 1..8, 5.0, 30.0, listOf("spell", "speed"), 1, 900),
            ModifierTemplate("Рока", EnumStatHelper.STOCK_CRITICAL_CHANCE, ModifierType.INCREASED, ModifierCategory.SUFFIX, 1..8, 10.0, 40.0, listOf("critical"), 1, 700),
            ModifierTemplate("Смерти", EnumStatHelper.STOCK_CRITICAL_DAMAGE, ModifierType.INCREASED, ModifierCategory.SUFFIX, 1..8, 15.0, 60.0, listOf("critical"), 1, 700)
        ))

        // Защитные префиксы
        templates.addAll(listOf(
            ModifierTemplate("Защитный", EnumStatHelper.STOCK_ARMOR, ModifierType.FLAT_ADD, ModifierCategory.PREFIX, 1..8, 10.0, 150.0, listOf("armor", "defence"), 1, 1000),
            ModifierTemplate("Призрачный", EnumStatHelper.STOCK_EVASION, ModifierType.INCREASED, ModifierCategory.PREFIX, 2..7, 10.0, 60.0, listOf("evasion"), 5, 800),
            ModifierTemplate("Барьерный", EnumStatHelper.STOCK_ENERGY_SHIELD, ModifierType.FLAT_ADD, ModifierCategory.PREFIX, 1..8, 5.0, 80.0, listOf("energy_shield"), 1, 900)
        ))

        // Сопротивления (суффиксы)
        listOf(
            Triple(EnumStatHelper.STOCK_RESIST_FIRE, "Огня", 15.0..45.0),
            Triple(EnumStatHelper.STOCK_RESIST_COLD, "Льда", 15.0..45.0),
            Triple(EnumStatHelper.STOCK_RESIST_LIGHTNING, "Молнии", 15.0..45.0),
            Triple(EnumStatHelper.STOCK_RESIST_CHAOS, "Хаоса", 8.0..35.0)
        ).forEach { (stat, name, range) ->
            templates.add(ModifierTemplate(
                name, stat, ModifierType.FLAT_ADD, ModifierCategory.SUFFIX, 1..8,
                range.start, range.endInclusive, listOf("resistance"), 1, 1000
            ))
        }

        // Жизнь и мана (префиксы)
        templates.addAll(listOf(
            ModifierTemplate("Здоровья", EnumStatHelper.STOCK_HEALTH, ModifierType.FLAT_ADD, ModifierCategory.PREFIX, 1..8, 10.0, 100.0, listOf("life"), 1, 1200),
            ModifierTemplate("Маны", EnumStatHelper.STOCK_MANA, ModifierType.FLAT_ADD, ModifierCategory.PREFIX, 1..8, 10.0, 80.0, listOf("mana"), 1, 1000)
        ))
    }

    fun generateRandomModifier(itemLevel: Int, category: ModifierCategory? = null, tag: String? = null): StatModifier? {
        printLog("category: $category")
        val pool = templates.filter { it.requiredItemLevel <= itemLevel && (category == null || it.category == category) }
            .filter { tag == null || it.tags.contains(tag) }
        if (pool.isEmpty()) return null

        val totalWeight = pool.sumOf { it.weight }
        var roll = (0..totalWeight).randomExt()
        val selected = pool.first {
            roll -= it.weight
            roll <= 0
        }

        val tier = when (selected.tierRange.randomExt()) {
            1 -> ModifierTier.TIER_1
            2 -> ModifierTier.TIER_2
            3 -> ModifierTier.TIER_3
            4 -> ModifierTier.TIER_4
            5 -> ModifierTier.TIER_5
            6 -> ModifierTier.TIER_6
            7 -> ModifierTier.TIER_7
            else -> ModifierTier.TIER_8
        }

        // Чем выше тир, тем выше значение
        val tierFactor = (9 - tier.tier) / 8.0
        val value = (selected.valueMin + (selected.valueMax - selected.valueMin) * tierFactor)
            .let { kotlin.math.round(it * 10) / 10.0 }

        return StatModifier(
            stat = selected.stat,
            type = selected.type,
            value = value,
            category = selected.category,
            tier = tier,
            name = "${selected.name} ${tier.name}",
            tag = selected.tags.firstOrNull()
        )
    }
}

// ================================ ХАРАКТЕРИСТИКИ ПЕРСОНАЖА (С УЧЁТОМ МОДИФИКАТОРОВ) ================================

data class CharacterStat(
    val stat: EnumStatHelper,
    var baseValue: Double = 0.0,
    private val modifiers: MutableList<StatModifier> = mutableListOf()
) {
    private fun getApplicableModifiers(): List<StatModifier> {
        return modifiers.filter { it.conflictsWith.none { conflict -> modifiers.any { m -> m.category == conflict } } }
    }

    fun addModifier(mod: StatModifier) {
        modifiers.add(mod)
    }

    fun removeModifier(mod: StatModifier) {
        modifiers.remove(mod)
    }

    fun calculateFinalValue(): Double {
        val applicable = getApplicableModifiers()
        val typeGroups = applicable.groupBy { it.type }
        val pairs = typeGroups.map { (type, mods) -> type to mods.sumOf { it.value } }
        return ModifierType.calculateFinal(baseValue, pairs)
    }

    fun getDescription(): String {
        val final = calculateFinalValue()
        val parts = mutableListOf<String>()
        if (baseValue != 0.0) parts.add("База: $baseValue")
        modifiers.groupBy { it.category }.forEach { (cat, list) ->
            parts.add("${cat.name}: ${list.joinToString(", ") { it.displayString() }}")
        }
        parts.add("Итог: $final")
        return parts.joinToString("\n")
    }
}
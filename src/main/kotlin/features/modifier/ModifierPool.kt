package features.modifier

import application.enums.EnumEquipmentType
import application.enums.EnumModifierCategory
import application.enums.EnumModifierGroup
import application.enums.EnumModifierType
import application.enums.EnumStatHelper
import application.enums.EnumStatModifierHelper

/**
 * Пул всех возможных модификаторов предметов.
 *
 * Построен из [EnumStatModifierHelper] + системы 8 тиров.
 *
 * ## Система тиров (PoE-стиль)
 * ```
 * T1 (лучший)  itemLevel >= 75   weight = 100
 * T2           itemLevel >= 60   weight = 200
 * T3           itemLevel >= 45   weight = 350
 * T4           itemLevel >= 35   weight = 550
 * T5           itemLevel >= 25   weight = 750
 * T6           itemLevel >= 15   weight = 900
 * T7           itemLevel >= 5    weight = 1000
 * T8 (худший)  itemLevel >= 1    weight = 1000
 * ```
 *
 * Значения между T8 и T1 интерполируются: `t = (8 - tier) / 7.0`
 */
object ModifierPool {

    /** Уровни предмета для каждого тира */
    private val TIER_MIN_ITEM_LEVELS = intArrayOf(75, 60, 45, 35, 25, 15, 5, 1)

    /** Веса тиров при случайном броске (T1..T8) */
    private val TIER_WEIGHTS = intArrayOf(100, 200, 350, 550, 750, 900, 1000, 1000)

    /** Все шаблоны явных аффиксов (PREFIX + SUFFIX) */
    val explicitTemplates: List<ModifierTemplate> = buildExplicitTemplates()

    /** Имплицитные шаблоны по слоту */
    val implicitTemplates: Map<EnumEquipmentType, List<ModifierTemplate>> = buildImplicitTemplates()

    // ===== PUBLIC API =====

    /**
     * Получить взвешенный пул явных аффиксов для генерации предмета.
     *
     * @param slot           Слот экипировки
     * @param itemLevel      Уровень предмета
     * @param category       Ограничение по категории (PREFIX/SUFFIX/null = оба)
     * @param usedGroups     Уже занятые группы (для исключения дублей)
     * @return Список (template, tierDef) с учётом весов и уровня предмета
     */
    fun getExplicitPool(
        slot: EnumEquipmentType,
        itemLevel: Int,
        category: EnumModifierCategory? = null,
        usedGroups: Set<EnumModifierGroup> = emptySet()
    ): List<Pair<ModifierTemplate, ModifierTierDef>> {
        return explicitTemplates
            .filter { t ->
                (category == null || t.category == category) &&
                    t.allowedSlots.contains(slot) &&
                    t.group !in usedGroups
            }
            .flatMap { template ->
                template.availableTiers(itemLevel).map { tier -> template to tier }
            }
    }

    /**
     * Получить пул имплицитных аффиксов для данного слота.
     *
     * @param slot       Слот экипировки
     * @param itemLevel  Уровень предмета
     */
    fun getImplicitPool(
        slot: EnumEquipmentType,
        itemLevel: Int
    ): List<Pair<ModifierTemplate, ModifierTierDef>> {
        return (implicitTemplates[slot] ?: emptyList())
            .flatMap { template ->
                template.availableTiers(itemLevel).map { tier -> template to tier }
            }
    }

    // ===== BUILDERS =====

    private fun buildExplicitTemplates(): List<ModifierTemplate> {
        val allSlots = EnumEquipmentType.entries.filter { it != EnumEquipmentType.UNDEFINED }.toSet()
        return EnumStatModifierHelper.entries.map { entry ->
            ModifierTemplate(
                source = entry,
                name = entry.displayName,
                stat = entry.stat,
                modType = entry.modType,
                category = entry.category,
                group = entry.group,
                tiers = buildTiers(entry.t8Min, entry.t8Max, entry.t1Min, entry.t1Max),
                allowedSlots = allSlots,
                tags = entry.tags,
                weight = entry.weight
            )
        }
    }

    private fun buildImplicitTemplates(): Map<EnumEquipmentType, List<ModifierTemplate>> {
        return mapOf(
            EnumEquipmentType.HELMET to listOf(
                implicitTemplate(
                    stat = EnumStatHelper.STOCK_ARMOR,
                    group = EnumModifierGroup.IMPLICIT_ARMOR,
                    name = "Базовая броня",
                    t8Min = 10.0, t8Max = 25.0, t1Min = 80.0, t1Max = 150.0
                ),
                implicitTemplate(
                    stat = EnumStatHelper.STOCK_ENERGY_SHIELD,
                    group = EnumModifierGroup.IMPLICIT_SHIELD,
                    name = "Базовый щит",
                    t8Min = 5.0, t8Max = 12.0, t1Min = 40.0, t1Max = 80.0
                )
            ),
            EnumEquipmentType.BODY to listOf(
                implicitTemplate(
                    stat = EnumStatHelper.STOCK_ARMOR,
                    group = EnumModifierGroup.IMPLICIT_ARMOR,
                    name = "Базовая броня",
                    t8Min = 20.0, t8Max = 50.0, t1Min = 150.0, t1Max = 300.0
                ),
                implicitTemplate(
                    stat = EnumStatHelper.STOCK_HEALTH,
                    group = EnumModifierGroup.IMPLICIT_LIFE,
                    name = "Базовая жизнь",
                    t8Min = 5.0, t8Max = 15.0, t1Min = 30.0, t1Max = 60.0
                )
            ),
            EnumEquipmentType.GLOVES to listOf(
                implicitTemplate(
                    stat = EnumStatHelper.STOCK_ATTACK_SPEED,
                    group = EnumModifierGroup.IMPLICIT_ATTACK_SPEED,
                    modType = EnumModifierType.INCREASED,
                    name = "Базовая скорость атаки",
                    t8Min = 1.0, t8Max = 3.0, t1Min = 5.0, t1Max = 12.0
                ),
                implicitTemplate(
                    stat = EnumStatHelper.STOCK_ARMOR,
                    group = EnumModifierGroup.IMPLICIT_ARMOR,
                    name = "Базовая броня",
                    t8Min = 8.0, t8Max = 20.0, t1Min = 50.0, t1Max = 120.0
                )
            ),
            EnumEquipmentType.BOOTS to listOf(
                implicitTemplate(
                    stat = EnumStatHelper.STOCK_MOVEMENT_SPEED,
                    group = EnumModifierGroup.IMPLICIT_MOVEMENT,
                    modType = EnumModifierType.INCREASED,
                    name = "Базовая скорость",
                    t8Min = 3.0, t8Max = 8.0, t1Min = 10.0, t1Max = 20.0
                ),
                implicitTemplate(
                    stat = EnumStatHelper.STOCK_EVASION,
                    group = EnumModifierGroup.IMPLICIT_EVASION,
                    name = "Базовое уклонение",
                    t8Min = 10.0, t8Max = 25.0, t1Min = 60.0, t1Max = 130.0
                )
            ),
            EnumEquipmentType.RING to listOf(
                implicitTemplate(
                    stat = EnumStatHelper.STOCK_MANA,
                    group = EnumModifierGroup.IMPLICIT_MANA,
                    name = "Базовая мана",
                    t8Min = 5.0, t8Max = 12.0, t1Min = 20.0, t1Max = 40.0
                ),
                implicitTemplate(
                    stat = EnumStatHelper.STOCK_RESIST_FIRE,
                    group = EnumModifierGroup.IMPLICIT_RESIST,
                    name = "Базовое сопр. огню",
                    t8Min = 3.0, t8Max = 8.0, t1Min = 15.0, t1Max = 25.0
                )
            )
        )
    }

    // ===== HELPERS =====

    /**
     * Построить 8 тиров путём линейной интерполяции между T8 и T1 диапазонами.
     * T1 = лучший (index 0), T8 = худший (index 7).
     */
    fun buildTiers(t8Min: Double, t8Max: Double, t1Min: Double, t1Max: Double): List<ModifierTierDef> {
        return (1..8).map { tier ->
            val t = (8 - tier) / 7.0   // tier 1 → 1.0, tier 8 → 0.0
            ModifierTierDef(
                tier = tier,
                minItemLevel = TIER_MIN_ITEM_LEVELS[tier - 1],
                minValue = lerp(t8Min, t1Min, t),
                maxValue = lerp(t8Max, t1Max, t),
                weight = TIER_WEIGHTS[tier - 1]
            )
        }
    }

    private fun lerp(a: Double, b: Double, t: Double) =
        (a + (b - a) * t).let { Math.round(it * 10) / 10.0 }

    private fun implicitTemplate(
        stat: EnumStatHelper,
        group: EnumModifierGroup,
        name: String,
        t8Min: Double, t8Max: Double, t1Min: Double, t1Max: Double,
        modType: EnumModifierType = EnumModifierType.FLAT_ADD
    ) = ModifierTemplate(
        source = EnumStatModifierHelper.HEALTH_FLAT, // placeholder, unused for implicits
        name = name,
        stat = stat,
        modType = modType,
        category = EnumModifierCategory.IMPLICIT,
        group = group,
        tiers = buildTiers(t8Min, t8Max, t1Min, t1Max),
        allowedSlots = EnumEquipmentType.entries.filter { it != EnumEquipmentType.UNDEFINED }.toSet(),
        tags = emptyList(),
        weight = 1000
    )
}

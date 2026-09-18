package config

import application.enums.EnumModifierOperation
import application.enums.EnumModifierSource
import application.enums.EnumStatStock
import application.enums.IntEnumStat
import extensions.to1Digits
import features.logic.modifiers.ModifierDefinition
import features.logic.modifiers.ModifierTier
import kotlin.math.roundToInt

/**
 * Начальные данные коллекций `ModifierDefinition` и `ModifierTier`.
 *
 * Набор модификаторов построен по модели Path of Exile:
 * - [EnumModifierSource] задаёт, откуда модификатор берётся: PREFIX и SUFFIX
 *   роллятся из пула предмета, IMPLICIT/ENCHANTMENT/CORRUPTION/UNIQUE висят на предмете всегда;
 * - [EnumModifierOperation] задаёт, как значение применяется: ADD - плоская прибавка,
 *   INCREASED - аддитивные проценты, MORE - мультипликативные проценты, SET - замена базы;
 * - тир 1 - лучший и требует самый высокий item level, дальше значения и требования падают.
 */
object ModifierSeeder {

    private data class TierTemplate(
        val tier: Int,
        val valueMin: Double,
        val valueMax: Double,
        val minItemLevel: Int,
    )

    private data class ModifierTemplate(
        val code: String,
        val name: String,
        val stat: IntEnumStat,
        val operation: EnumModifierOperation,
        val source: EnumModifierSource,
        val tiers: List<TierTemplate>,
        val tags: List<String> = emptyList(),
    )

    /**
     * Разворачивает тиры модификатора между лучшим (тир 1) и худшим (тир [count]).
     *
     * Значения и требуемый item level интерполируются линейно, так что
     * достаточно задать только границы, как в таблицах модификаторов POE.
     */
    private fun tiers(
        count: Int,
        best: ClosedFloatingPointRange<Double>,
        worst: ClosedFloatingPointRange<Double>,
        bestItemLevel: Int,
        worstItemLevel: Int = 1,
    ): List<TierTemplate> = (1..count).map { tier ->
        val progress = if (count == 1) 0.0 else (tier - 1).toDouble() / (count - 1).toDouble()

        TierTemplate(
            tier = tier,
            valueMin = lerp(best.start, worst.start, progress),
            valueMax = lerp(best.endInclusive, worst.endInclusive, progress),
            minItemLevel = lerp(bestItemLevel.toDouble(), worstItemLevel.toDouble(), progress).roundToInt().coerceAtLeast(1)
        )
    }

    private fun lerp(from: Double, to: Double, progress: Double) = (from + (to - from) * progress).to1Digits()

    // ==================== Таблица модификаторов ====================

    private val templates = listOf(

        // ---------- PREFIX: запас характеристик ----------
        ModifierTemplate(
            code = "ADD_MAXIMUM_LIFE",
            name = "+# to maximum Life",
            stat = EnumStatStock.STOCK_HEALTH,
            operation = EnumModifierOperation.ADD,
            source = EnumModifierSource.PREFIX,
            tags = listOf("life", "defences"),
            tiers = tiers(count = 8, best = 120.0..129.0, worst = 10.0..19.0, bestItemLevel = 86)
        ),
        ModifierTemplate(
            code = "ADD_MAXIMUM_MANA",
            name = "+# to maximum Mana",
            stat = EnumStatStock.STOCK_MANA,
            operation = EnumModifierOperation.ADD,
            source = EnumModifierSource.PREFIX,
            tags = listOf("mana", "caster"),
            tiers = tiers(count = 8, best = 129.0..142.0, worst = 15.0..19.0, bestItemLevel = 85)
        ),
        ModifierTemplate(
            code = "ADD_ENERGY_SHIELD",
            name = "+# to maximum Energy Shield",
            stat = EnumStatStock.STOCK_ENERGY_SHIELD,
            operation = EnumModifierOperation.ADD,
            source = EnumModifierSource.PREFIX,
            tags = listOf("energy_shield", "defences"),
            tiers = tiers(count = 8, best = 65.0..71.0, worst = 3.0..5.0, bestItemLevel = 86)
        ),

        // ---------- PREFIX: защита ----------
        ModifierTemplate(
            code = "ADD_ARMOUR",
            name = "+# to Armour",
            stat = EnumStatStock.STOCK_ARMOR,
            operation = EnumModifierOperation.ADD,
            source = EnumModifierSource.PREFIX,
            tags = listOf("armour", "defences"),
            tiers = tiers(count = 8, best = 380.0..440.0, worst = 8.0..15.0, bestItemLevel = 84)
        ),
        ModifierTemplate(
            code = "INCREASED_ARMOUR",
            name = "#% increased Armour",
            stat = EnumStatStock.STOCK_ARMOR,
            operation = EnumModifierOperation.INCREASED,
            source = EnumModifierSource.PREFIX,
            tags = listOf("armour", "defences"),
            tiers = tiers(count = 8, best = 100.0..109.0, worst = 6.0..13.0, bestItemLevel = 84)
        ),
        ModifierTemplate(
            code = "ADD_EVASION_RATING",
            name = "+# to Evasion Rating",
            stat = EnumStatStock.STOCK_EVASION,
            operation = EnumModifierOperation.ADD,
            source = EnumModifierSource.PREFIX,
            tags = listOf("evasion", "defences"),
            tiers = tiers(count = 8, best = 380.0..440.0, worst = 8.0..15.0, bestItemLevel = 84)
        ),
        ModifierTemplate(
            code = "INCREASED_EVASION_RATING",
            name = "#% increased Evasion Rating",
            stat = EnumStatStock.STOCK_EVASION,
            operation = EnumModifierOperation.INCREASED,
            source = EnumModifierSource.PREFIX,
            tags = listOf("evasion", "defences"),
            tiers = tiers(count = 8, best = 100.0..109.0, worst = 6.0..13.0, bestItemLevel = 84)
        ),
        ModifierTemplate(
            code = "INCREASED_ENERGY_SHIELD",
            name = "#% increased maximum Energy Shield",
            stat = EnumStatStock.STOCK_ENERGY_SHIELD,
            operation = EnumModifierOperation.INCREASED,
            source = EnumModifierSource.PREFIX,
            tags = listOf("energy_shield", "defences"),
            tiers = tiers(count = 8, best = 100.0..109.0, worst = 6.0..13.0, bestItemLevel = 84)
        ),

        // ---------- PREFIX: урон ----------
        ModifierTemplate(
            code = "ADD_PHYSICAL_DAMAGE",
            name = "Adds # Physical Damage",
            stat = EnumStatStock.STOCK_ATTACK_PHYSICAL,
            operation = EnumModifierOperation.ADD,
            source = EnumModifierSource.PREFIX,
            tags = listOf("physical", "damage", "attack"),
            tiers = tiers(count = 8, best = 25.0..45.0, worst = 1.0..3.0, bestItemLevel = 77)
        ),
        ModifierTemplate(
            code = "INCREASED_PHYSICAL_DAMAGE",
            name = "#% increased Physical Damage",
            stat = EnumStatStock.STOCK_ATTACK_PHYSICAL,
            operation = EnumModifierOperation.INCREASED,
            source = EnumModifierSource.PREFIX,
            tags = listOf("physical", "damage", "attack"),
            tiers = tiers(count = 8, best = 170.0..179.0, worst = 40.0..49.0, bestItemLevel = 81)
        ),
        ModifierTemplate(
            code = "ADD_FIRE_DAMAGE",
            name = "Adds # Fire Damage",
            stat = EnumStatStock.STOCK_ATTACK_FIRE,
            operation = EnumModifierOperation.ADD,
            source = EnumModifierSource.PREFIX,
            tags = listOf("fire", "elemental", "damage", "attack"),
            tiers = tiers(count = 8, best = 30.0..55.0, worst = 1.0..3.0, bestItemLevel = 82)
        ),
        ModifierTemplate(
            code = "ADD_COLD_DAMAGE",
            name = "Adds # Cold Damage",
            stat = EnumStatStock.STOCK_ATTACK_COLD,
            operation = EnumModifierOperation.ADD,
            source = EnumModifierSource.PREFIX,
            tags = listOf("cold", "elemental", "damage", "attack"),
            tiers = tiers(count = 8, best = 27.0..50.0, worst = 1.0..3.0, bestItemLevel = 81)
        ),
        ModifierTemplate(
            code = "ADD_LIGHTNING_DAMAGE",
            name = "Adds # Lightning Damage",
            stat = EnumStatStock.STOCK_ATTACK_LIGHTNING,
            operation = EnumModifierOperation.ADD,
            source = EnumModifierSource.PREFIX,
            tags = listOf("lightning", "elemental", "damage", "attack"),
            tiers = tiers(count = 8, best = 2.0..90.0, worst = 1.0..5.0, bestItemLevel = 83)
        ),
        ModifierTemplate(
            code = "ADD_CHAOS_DAMAGE",
            name = "Adds # Chaos Damage",
            stat = EnumStatStock.STOCK_ATTACK_CHAOS,
            operation = EnumModifierOperation.ADD,
            source = EnumModifierSource.PREFIX,
            tags = listOf("chaos", "damage", "attack"),
            tiers = tiers(count = 6, best = 20.0..38.0, worst = 2.0..4.0, bestItemLevel = 80)
        ),
        ModifierTemplate(
            code = "INCREASED_SPELL_DAMAGE",
            name = "#% increased Spell Damage",
            stat = EnumStatStock.STOCK_ATTACK_MAGICAL,
            operation = EnumModifierOperation.INCREASED,
            source = EnumModifierSource.PREFIX,
            tags = listOf("caster", "damage"),
            tiers = tiers(count = 8, best = 90.0..104.0, worst = 10.0..14.0, bestItemLevel = 84)
        ),

        // ---------- SUFFIX: атрибуты ----------
        ModifierTemplate(
            code = "ADD_STRENGTH",
            name = "+# to Strength",
            stat = EnumStatStock.STOCK_STRENGTH,
            operation = EnumModifierOperation.ADD,
            source = EnumModifierSource.SUFFIX,
            tags = listOf("attribute", "strength"),
            tiers = tiers(count = 8, best = 50.0..54.0, worst = 8.0..12.0, bestItemLevel = 82)
        ),
        ModifierTemplate(
            code = "ADD_DEXTERITY",
            name = "+# to Dexterity",
            stat = EnumStatStock.STOCK_AGILITY,
            operation = EnumModifierOperation.ADD,
            source = EnumModifierSource.SUFFIX,
            tags = listOf("attribute", "dexterity"),
            tiers = tiers(count = 8, best = 50.0..54.0, worst = 8.0..12.0, bestItemLevel = 82)
        ),
        ModifierTemplate(
            code = "ADD_INTELLIGENCE",
            name = "+# to Intelligence",
            stat = EnumStatStock.STOCK_INTELLECT,
            operation = EnumModifierOperation.ADD,
            source = EnumModifierSource.SUFFIX,
            tags = listOf("attribute", "intelligence"),
            tiers = tiers(count = 8, best = 50.0..54.0, worst = 8.0..12.0, bestItemLevel = 82)
        ),

        // ---------- SUFFIX: сопротивления ----------
        ModifierTemplate(
            code = "ADD_FIRE_RESISTANCE",
            name = "+#% to Fire Resistance",
            stat = EnumStatStock.STOCK_RESIST_FIRE,
            operation = EnumModifierOperation.ADD,
            source = EnumModifierSource.SUFFIX,
            tags = listOf("resistance", "fire", "elemental"),
            tiers = tiers(count = 8, best = 46.0..48.0, worst = 6.0..11.0, bestItemLevel = 84)
        ),
        ModifierTemplate(
            code = "ADD_COLD_RESISTANCE",
            name = "+#% to Cold Resistance",
            stat = EnumStatStock.STOCK_RESIST_COLD,
            operation = EnumModifierOperation.ADD,
            source = EnumModifierSource.SUFFIX,
            tags = listOf("resistance", "cold", "elemental"),
            tiers = tiers(count = 8, best = 46.0..48.0, worst = 6.0..11.0, bestItemLevel = 84)
        ),
        ModifierTemplate(
            code = "ADD_LIGHTNING_RESISTANCE",
            name = "+#% to Lightning Resistance",
            stat = EnumStatStock.STOCK_RESIST_LIGHTNING,
            operation = EnumModifierOperation.ADD,
            source = EnumModifierSource.SUFFIX,
            tags = listOf("resistance", "lightning", "elemental"),
            tiers = tiers(count = 8, best = 46.0..48.0, worst = 6.0..11.0, bestItemLevel = 84)
        ),
        ModifierTemplate(
            code = "ADD_CHAOS_RESISTANCE",
            name = "+#% to Chaos Resistance",
            stat = EnumStatStock.STOCK_RESIST_CHAOS,
            operation = EnumModifierOperation.ADD,
            source = EnumModifierSource.SUFFIX,
            tags = listOf("resistance", "chaos"),
            tiers = tiers(count = 6, best = 31.0..35.0, worst = 5.0..10.0, bestItemLevel = 81)
        ),
        ModifierTemplate(
            code = "ADD_ALL_ELEMENTAL_RESISTANCES",
            name = "+#% to all Elemental Resistances",
            stat = EnumStatStock.STOCK_RESIST_ALL,
            operation = EnumModifierOperation.ADD,
            source = EnumModifierSource.SUFFIX,
            tags = listOf("resistance", "elemental"),
            tiers = tiers(count = 6, best = 15.0..16.0, worst = 3.0..5.0, bestItemLevel = 84)
        ),

        // ---------- SUFFIX: скорость ----------
        ModifierTemplate(
            code = "INCREASED_ATTACK_SPEED",
            name = "#% increased Attack Speed",
            stat = EnumStatStock.STOCK_ATTACK_SPEED,
            operation = EnumModifierOperation.INCREASED,
            source = EnumModifierSource.SUFFIX,
            tags = listOf("speed", "attack"),
            tiers = tiers(count = 7, best = 25.0..27.0, worst = 5.0..7.0, bestItemLevel = 77)
        ),
        ModifierTemplate(
            code = "INCREASED_CAST_SPEED",
            name = "#% increased Cast Speed",
            stat = EnumStatStock.STOCK_CAST_SPEED,
            operation = EnumModifierOperation.INCREASED,
            source = EnumModifierSource.SUFFIX,
            tags = listOf("speed", "caster"),
            tiers = tiers(count = 7, best = 20.0..22.0, worst = 5.0..7.0, bestItemLevel = 77)
        ),
        ModifierTemplate(
            code = "INCREASED_MOVEMENT_SPEED",
            name = "#% increased Movement Speed",
            stat = EnumStatStock.STOCK_MOVEMENT_SPEED,
            operation = EnumModifierOperation.INCREASED,
            source = EnumModifierSource.SUFFIX,
            tags = listOf("speed"),
            tiers = tiers(count = 4, best = 30.0..34.0, worst = 10.0..14.0, bestItemLevel = 86, worstItemLevel = 1)
        ),

        // ---------- SUFFIX: криты, реген, лич ----------
        ModifierTemplate(
            code = "INCREASED_CRITICAL_STRIKE_CHANCE",
            name = "#% increased Critical Strike Chance",
            stat = EnumStatStock.STOCK_CRITICAL_CHANCE,
            operation = EnumModifierOperation.INCREASED,
            source = EnumModifierSource.SUFFIX,
            tags = listOf("critical"),
            tiers = tiers(count = 7, best = 35.0..38.0, worst = 10.0..14.0, bestItemLevel = 81)
        ),
        ModifierTemplate(
            code = "ADD_CRITICAL_STRIKE_MULTIPLIER",
            name = "+#% to Critical Strike Multiplier",
            stat = EnumStatStock.STOCK_CRITICAL_MULTIPLIER,
            operation = EnumModifierOperation.ADD,
            source = EnumModifierSource.SUFFIX,
            tags = listOf("critical"),
            tiers = tiers(count = 7, best = 35.0..38.0, worst = 10.0..14.0, bestItemLevel = 81)
        ),
        ModifierTemplate(
            code = "ADD_LIFE_REGENERATION",
            name = "Regenerate # Life per second",
            stat = EnumStatStock.STOCK_HEALTH_REGEN,
            operation = EnumModifierOperation.ADD,
            source = EnumModifierSource.SUFFIX,
            tags = listOf("life", "regen"),
            tiers = tiers(count = 7, best = 20.0..25.0, worst = 1.0..2.0, bestItemLevel = 84)
        ),
        ModifierTemplate(
            code = "INCREASED_MANA_REGENERATION",
            name = "#% increased Mana Regeneration Rate",
            stat = EnumStatStock.STOCK_MANA_REGEN,
            operation = EnumModifierOperation.INCREASED,
            source = EnumModifierSource.SUFFIX,
            tags = listOf("mana", "regen", "caster"),
            tiers = tiers(count = 7, best = 60.0..69.0, worst = 10.0..19.0, bestItemLevel = 80)
        ),
        ModifierTemplate(
            code = "ADD_PHYSICAL_LIFE_LEECH",
            name = "#% of Physical Attack Damage Leeched as Life",
            stat = EnumStatStock.STOCK_LEECH_PHYSICAL,
            operation = EnumModifierOperation.ADD,
            source = EnumModifierSource.SUFFIX,
            tags = listOf("leech", "physical", "attack"),
            tiers = tiers(count = 4, best = 1.2..1.4, worst = 0.2..0.4, bestItemLevel = 79)
        ),
        ModifierTemplate(
            code = "ADD_BLOCK_CHANCE",
            name = "+#% Chance to Block",
            stat = EnumStatStock.STOCK_BLOCK_CHANCE,
            operation = EnumModifierOperation.ADD,
            source = EnumModifierSource.SUFFIX,
            tags = listOf("block", "defences"),
            tiers = tiers(count = 4, best = 5.0..6.0, worst = 1.0..2.0, bestItemLevel = 80)
        ),
        ModifierTemplate(
            code = "INCREASED_ITEM_RARITY",
            name = "#% increased Rarity of Items found",
            stat = EnumStatStock.STOCK_RARITY,
            operation = EnumModifierOperation.INCREASED,
            source = EnumModifierSource.SUFFIX,
            tags = listOf("rarity"),
            tiers = tiers(count = 5, best = 20.0..24.0, worst = 6.0..10.0, bestItemLevel = 82)
        ),

        // ---------- IMPLICIT: встроенные модификаторы базы ----------
        ModifierTemplate(
            code = "IMPLICIT_ADD_MAXIMUM_LIFE",
            name = "+# to maximum Life",
            stat = EnumStatStock.STOCK_HEALTH,
            operation = EnumModifierOperation.ADD,
            source = EnumModifierSource.IMPLICIT,
            tags = listOf("life", "implicit"),
            tiers = tiers(count = 3, best = 40.0..45.0, worst = 10.0..15.0, bestItemLevel = 60)
        ),
        ModifierTemplate(
            code = "IMPLICIT_ADD_ARMOUR",
            name = "+# to Armour",
            stat = EnumStatStock.STOCK_ARMOR,
            operation = EnumModifierOperation.ADD,
            source = EnumModifierSource.IMPLICIT,
            tags = listOf("armour", "defences", "implicit"),
            tiers = tiers(count = 3, best = 100.0..120.0, worst = 20.0..30.0, bestItemLevel = 60)
        ),
        ModifierTemplate(
            code = "IMPLICIT_ADD_ALL_ELEMENTAL_RESISTANCES",
            name = "+#% to all Elemental Resistances",
            stat = EnumStatStock.STOCK_RESIST_ALL,
            operation = EnumModifierOperation.ADD,
            source = EnumModifierSource.IMPLICIT,
            tags = listOf("resistance", "elemental", "implicit"),
            tiers = tiers(count = 3, best = 12.0..16.0, worst = 4.0..6.0, bestItemLevel = 60)
        ),

        // ---------- ENCHANTMENT: лабиринтные энчанты ----------
        ModifierTemplate(
            code = "ENCHANT_INCREASED_MOVEMENT_SPEED",
            name = "#% increased Movement Speed",
            stat = EnumStatStock.STOCK_MOVEMENT_SPEED,
            operation = EnumModifierOperation.INCREASED,
            source = EnumModifierSource.ENCHANTMENT,
            tags = listOf("speed", "enchantment"),
            tiers = tiers(count = 3, best = 16.0..16.0, worst = 8.0..8.0, bestItemLevel = 68)
        ),
        ModifierTemplate(
            code = "ENCHANT_INCREASED_AURA_EFFECT",
            name = "#% increased effect of Auras",
            stat = EnumStatStock.STOCK_AURA_EFFECT,
            operation = EnumModifierOperation.INCREASED,
            source = EnumModifierSource.ENCHANTMENT,
            tags = listOf("aura", "enchantment"),
            tiers = tiers(count = 3, best = 12.0..12.0, worst = 4.0..4.0, bestItemLevel = 68)
        ),

        // ---------- CORRUPTION: vaal-модификаторы ----------
        ModifierTemplate(
            code = "CORRUPTED_ADD_ALL_ELEMENTAL_RESISTANCES",
            name = "+#% to all Elemental Resistances",
            stat = EnumStatStock.STOCK_RESIST_ALL,
            operation = EnumModifierOperation.ADD,
            source = EnumModifierSource.CORRUPTION,
            tags = listOf("resistance", "elemental", "corrupted"),
            tiers = tiers(count = 2, best = 10.0..12.0, worst = 5.0..7.0, bestItemLevel = 68)
        ),
        ModifierTemplate(
            code = "CORRUPTED_MORE_PHYSICAL_DAMAGE",
            name = "#% more Physical Damage",
            stat = EnumStatStock.STOCK_ATTACK_PHYSICAL,
            operation = EnumModifierOperation.MORE,
            source = EnumModifierSource.CORRUPTION,
            tags = listOf("physical", "damage", "corrupted"),
            tiers = tiers(count = 2, best = 12.0..15.0, worst = 5.0..8.0, bestItemLevel = 68)
        ),

        // ---------- UNIQUE: модификаторы уникальных предметов ----------
        ModifierTemplate(
            code = "UNIQUE_MORE_MAXIMUM_LIFE",
            name = "#% more maximum Life",
            stat = EnumStatStock.STOCK_HEALTH,
            operation = EnumModifierOperation.MORE,
            source = EnumModifierSource.UNIQUE,
            tags = listOf("life", "unique"),
            tiers = tiers(count = 1, best = 10.0..10.0, worst = 10.0..10.0, bestItemLevel = 70)
        ),
        ModifierTemplate(
            code = "UNIQUE_SET_CRITICAL_STRIKE_CHANCE",
            name = "Your Critical Strike Chance is #%",
            stat = EnumStatStock.STOCK_CRITICAL_CHANCE,
            operation = EnumModifierOperation.SET,
            source = EnumModifierSource.UNIQUE,
            tags = listOf("critical", "unique"),
            tiers = tiers(count = 1, best = 5.0..5.0, worst = 5.0..5.0, bestItemLevel = 70)
        ),
    )

    /**
     * Документы коллекции `ModifierDefinition`.
     */
    fun seedDefinitions(): List<ModifierDefinition> = templates.map { template ->
        ModifierDefinition(
            code = template.code,
            stat = template.stat,
            operation = template.operation,
            source = template.source,
            name = template.name,
            tags = template.tags.toMutableList()
        )
    }

    /**
     * Документы коллекции `ModifierTier` для уже сохранённых описаний.
     *
     * @param definitions описания, которым принадлежат тиры (нужны их _id)
     */
    fun seedTiers(definitions: List<ModifierDefinition>): List<ModifierTier> {
        val templatesByCode = templates.associateBy { it.code }

        return definitions.flatMap { definition ->
            val template = templatesByCode[definition.code] ?: return@flatMap emptyList()
            template.tiers.map { tier ->
                ModifierTier(
                    modifierId = definition._id,
                    tier = tier.tier,
                    valueMin = tier.valueMin,
                    valueMax = tier.valueMax,
                    minItemLevel = tier.minItemLevel,
                    // Чем лучше тир, тем реже он выпадает среди доступных
                    weight = tier.tier
                )
            }
        }
    }
}

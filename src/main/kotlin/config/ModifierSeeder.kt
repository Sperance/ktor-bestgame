package config

import application.enums.EnumModifierOperation
import application.enums.EnumModifierOperation.ADD
import application.enums.EnumModifierOperation.INCREASED
import application.enums.EnumModifierOperation.MORE
import application.enums.EnumModifierOperation.SET
import application.enums.EnumModifierSource
import application.enums.EnumModifierSource.CORRUPTION
import application.enums.EnumModifierSource.ENCHANTMENT
import application.enums.EnumModifierSource.IMPLICIT
import application.enums.EnumModifierSource.PASSIVE
import application.enums.EnumModifierSource.PREFIX
import application.enums.EnumModifierSource.SUFFIX
import application.enums.EnumStatStock.STOCK_AGILITY
import application.enums.EnumStatStock.STOCK_ARMOR
import application.enums.EnumStatStock.STOCK_ATTACK_CHAOS
import application.enums.EnumStatStock.STOCK_ATTACK_COLD
import application.enums.EnumStatStock.STOCK_ATTACK_FIRE
import application.enums.EnumStatStock.STOCK_ATTACK_LIGHTNING
import application.enums.EnumStatStock.STOCK_ATTACK_MAGICAL
import application.enums.EnumStatStock.STOCK_ATTACK_PHYSICAL
import application.enums.EnumStatStock.STOCK_ATTACK_SPEED
import application.enums.EnumStatStock.STOCK_AURA_EFFECT
import application.enums.EnumStatStock.STOCK_BLOCK_CHANCE
import application.enums.EnumStatStock.STOCK_CAST_SPEED
import application.enums.EnumStatStock.STOCK_CRITICAL_CHANCE
import application.enums.EnumStatStock.STOCK_CRITICAL_MULTIPLIER
import application.enums.EnumStatStock.STOCK_ENERGY_SHIELD
import application.enums.EnumStatStock.STOCK_EVASION
import application.enums.EnumStatStock.STOCK_HEALTH
import application.enums.EnumStatStock.STOCK_HEALTH_REGEN
import application.enums.EnumStatStock.STOCK_INTELLECT
import application.enums.EnumStatStock.STOCK_LEECH_PHYSICAL
import application.enums.EnumStatStock.STOCK_MANA
import application.enums.EnumStatStock.STOCK_MANA_REGEN
import application.enums.EnumStatStock.STOCK_MOVEMENT_SPEED
import application.enums.EnumStatStock.STOCK_RARITY
import application.enums.EnumStatStock.STOCK_RESIST_ALL
import application.enums.EnumStatStock.STOCK_RESIST_CHAOS
import application.enums.EnumStatStock.STOCK_RESIST_COLD
import application.enums.EnumStatStock.STOCK_RESIST_FIRE
import application.enums.EnumStatStock.STOCK_RESIST_LIGHTNING
import application.enums.EnumStatStock.STOCK_STRENGTH
import application.enums.EnumStatStock.STOCK_STUN_THRESHOLD
import features.logic.modifiers.ModifierDefinition
import features.logic.modifiers.ModifierTier

/**
 * Начальные данные коллекций `ModifierDefinition` и `ModifierTier`.
 *
 * Набор модификаторов построен по модели Path of Exile:
 * - [EnumModifierSource] задаёт, откуда модификатор берётся: PREFIX и SUFFIX
 *   роллятся из пула предмета, IMPLICIT/ENCHANTMENT/CORRUPTION/UNIQUE висят на предмете всегда;
 * - [EnumModifierOperation] задаёт, как значение применяется: ADD - плоская прибавка,
 *   INCREASED - аддитивные проценты, MORE - мультипликативные проценты, SET - замена базы;
 * - модификатор может быть составным и менять несколько статов сразу
 *   ("+# to maximum Life and Mana"), тогда у него несколько эффектов;
 * - тир 1 - лучший и требует самый высокий item level, дальше значения и требования падают.
 */
object ModifierSeeder {

    // ==================== Таблица модификаторов ====================

    private val templates = listOf(

        // ---------- PREFIX: запас характеристик ----------
        ModifierTemplate(
            code = "ADD_MAXIMUM_LIFE",
            name = "+# to maximum Life",
            source = PREFIX,
            tags = listOf("life", "defences"),
            effects = listOf(effect(STOCK_HEALTH, ADD, best = 120.0..129.0, worst = 10.0..19.0)),
            tierCount = 8, bestItemLevel = 86
        ),
        ModifierTemplate(
            code = "ADD_MAXIMUM_MANA",
            name = "+# to maximum Mana",
            source = PREFIX,
            tags = listOf("mana", "caster"),
            effects = listOf(effect(STOCK_MANA, ADD, best = 129.0..142.0, worst = 15.0..19.0)),
            tierCount = 8, bestItemLevel = 85
        ),
        ModifierTemplate(
            code = "ADD_ENERGY_SHIELD",
            name = "+# to maximum Energy Shield",
            source = PASSIVE,
            tags = listOf("energy_shield", "defences"),
            effects = listOf(effect(STOCK_ENERGY_SHIELD, ADD, best = 65.0..71.0, worst = 3.0..5.0)),
            tierCount = 8, bestItemLevel = 86
        ),

        // ---------- PREFIX: защита ----------
        ModifierTemplate(
            code = "ADD_ARMOUR",
            name = "+# to Armour",
            source = PASSIVE,
            tags = listOf("armour", "defences"),
            effects = listOf(effect(STOCK_ARMOR, ADD, best = 380.0..440.0, worst = 8.0..15.0)),
            tierCount = 8, bestItemLevel = 84
        ),
        ModifierTemplate(
            code = "INCREASED_ARMOUR",
            name = "#% increased Armour",
            source = PASSIVE,
            tags = listOf("armour", "defences"),
            effects = listOf(effect(STOCK_ARMOR, INCREASED, best = 100.0..109.0, worst = 6.0..13.0)),
            tierCount = 8, bestItemLevel = 84
        ),
        ModifierTemplate(
            code = "ADD_EVASION_RATING",
            name = "+# to Evasion Rating",
            source = PASSIVE,
            tags = listOf("evasion", "defences"),
            effects = listOf(effect(STOCK_EVASION, ADD, best = 380.0..440.0, worst = 8.0..15.0)),
            tierCount = 8, bestItemLevel = 84
        ),
        ModifierTemplate(
            code = "INCREASED_EVASION_RATING",
            name = "#% increased Evasion Rating",
            source = PASSIVE,
            tags = listOf("evasion", "defences"),
            effects = listOf(effect(STOCK_EVASION, INCREASED, best = 100.0..109.0, worst = 6.0..13.0)),
            tierCount = 8, bestItemLevel = 84
        ),
        ModifierTemplate(
            code = "INCREASED_ENERGY_SHIELD",
            name = "#% increased maximum Energy Shield",
            source = PASSIVE,
            tags = listOf("energy_shield", "defences"),
            effects = listOf(effect(STOCK_ENERGY_SHIELD, INCREASED, best = 100.0..109.0, worst = 6.0..13.0)),
            tierCount = 8, bestItemLevel = 84
        ),

        // ---------- PREFIX: урон ----------
        ModifierTemplate(
            code = "ADD_PHYSICAL_DAMAGE",
            name = "Adds # Physical Damage",
            source = PASSIVE,
            tags = listOf("physical", "damage", "attack"),
            effects = listOf(effect(STOCK_ATTACK_PHYSICAL, ADD, best = 25.0..45.0, worst = 1.0..3.0)),
            tierCount = 8, bestItemLevel = 77
        ),
        ModifierTemplate(
            code = "INCREASED_PHYSICAL_DAMAGE",
            name = "#% increased Physical Damage",
            source = PASSIVE,
            tags = listOf("physical", "damage", "attack"),
            effects = listOf(effect(STOCK_ATTACK_PHYSICAL, INCREASED, best = 170.0..179.0, worst = 40.0..49.0)),
            tierCount = 8, bestItemLevel = 81
        ),
        ModifierTemplate(
            code = "ADD_FIRE_DAMAGE",
            name = "Adds # Fire Damage",
            source = PREFIX,
            tags = listOf("fire", "elemental", "damage", "attack"),
            effects = listOf(effect(STOCK_ATTACK_FIRE, ADD, best = 30.0..55.0, worst = 1.0..3.0)),
            tierCount = 8, bestItemLevel = 82
        ),
        ModifierTemplate(
            code = "ADD_COLD_DAMAGE",
            name = "Adds # Cold Damage",
            source = PREFIX,
            tags = listOf("cold", "elemental", "damage", "attack"),
            effects = listOf(effect(STOCK_ATTACK_COLD, ADD, best = 27.0..50.0, worst = 1.0..3.0)),
            tierCount = 8, bestItemLevel = 81
        ),
        ModifierTemplate(
            code = "ADD_LIGHTNING_DAMAGE",
            name = "Adds # Lightning Damage",
            source = PREFIX,
            tags = listOf("lightning", "elemental", "damage", "attack"),
            effects = listOf(effect(STOCK_ATTACK_LIGHTNING, ADD, best = 2.0..90.0, worst = 1.0..5.0)),
            tierCount = 8, bestItemLevel = 83
        ),
        ModifierTemplate(
            code = "ADD_CHAOS_DAMAGE",
            name = "Adds # Chaos Damage",
            source = PREFIX,
            tags = listOf("chaos", "damage", "attack"),
            effects = listOf(effect(STOCK_ATTACK_CHAOS, ADD, best = 20.0..38.0, worst = 2.0..4.0)),
            tierCount = 6, bestItemLevel = 80
        ),
        ModifierTemplate(
            code = "INCREASED_SPELL_DAMAGE",
            name = "#% increased Spell Damage",
            source = PREFIX,
            tags = listOf("caster", "damage"),
            effects = listOf(effect(STOCK_ATTACK_MAGICAL, INCREASED, best = 90.0..104.0, worst = 10.0..14.0)),
            tierCount = 8, bestItemLevel = 84
        ),

        // ---------- PREFIX: составные ----------
        ModifierTemplate(
            code = "ADD_MAXIMUM_LIFE_AND_MANA",
            name = "+# to maximum Life and Mana",
            source = PREFIX,
            tags = listOf("life", "mana", "hybrid"),
            effects = listOf(
                effect(STOCK_HEALTH, ADD, best = 55.0..64.0, worst = 10.0..14.0),
                effect(STOCK_MANA, ADD, best = 55.0..64.0, worst = 10.0..14.0),
            ),
            tierCount = 6, bestItemLevel = 82
        ),
        ModifierTemplate(
            code = "INCREASED_ARMOUR_AND_EVASION",
            name = "#% increased Armour and Evasion",
            source = PASSIVE,
            tags = listOf("armour", "evasion", "defences", "hybrid"),
            effects = listOf(
                effect(STOCK_ARMOR, INCREASED, best = 82.0..90.0, worst = 6.0..13.0),
                effect(STOCK_EVASION, INCREASED, best = 82.0..90.0, worst = 6.0..13.0),
            ),
            tierCount = 8, bestItemLevel = 84
        ),
        ModifierTemplate(
            code = "INCREASED_ARMOUR_AND_ENERGY_SHIELD",
            name = "#% increased Armour and Energy Shield",
            source = PASSIVE,
            tags = listOf("armour", "energy_shield", "defences", "hybrid"),
            effects = listOf(
                effect(STOCK_ARMOR, INCREASED, best = 82.0..90.0, worst = 6.0..13.0),
                effect(STOCK_ENERGY_SHIELD, INCREASED, best = 82.0..90.0, worst = 6.0..13.0),
            ),
            tierCount = 8, bestItemLevel = 84
        ),
        ModifierTemplate(
            code = "INCREASED_EVASION_AND_ENERGY_SHIELD",
            name = "#% increased Evasion and Energy Shield",
            source = PASSIVE,
            tags = listOf("evasion", "energy_shield", "defences", "hybrid"),
            effects = listOf(
                effect(STOCK_EVASION, INCREASED, best = 82.0..90.0, worst = 6.0..13.0),
                effect(STOCK_ENERGY_SHIELD, INCREASED, best = 82.0..90.0, worst = 6.0..13.0),
            ),
            tierCount = 8, bestItemLevel = 84
        ),
        ModifierTemplate(
            code = "INCREASED_SPELL_DAMAGE_AND_MANA",
            name = "#% increased Spell Damage / +# to maximum Mana",
            source = PREFIX,
            tags = listOf("caster", "damage", "mana", "hybrid"),
            effects = listOf(
                effect(STOCK_ATTACK_MAGICAL, INCREASED, best = 60.0..69.0, worst = 10.0..14.0),
                effect(STOCK_MANA, ADD, best = 60.0..74.0, worst = 10.0..19.0),
            ),
            tierCount = 7, bestItemLevel = 84
        ),
        ModifierTemplate(
            code = "INCREASED_PHYSICAL_DAMAGE_AND_CRITICAL_MULTIPLIER",
            name = "#% increased Physical Damage / +#% to Critical Strike Multiplier",
            source = PREFIX,
            tags = listOf("physical", "damage", "critical", "attack", "hybrid"),
            effects = listOf(
                effect(STOCK_ATTACK_PHYSICAL, INCREASED, best = 100.0..119.0, worst = 30.0..39.0),
                effect(STOCK_CRITICAL_MULTIPLIER, ADD, best = 20.0..24.0, worst = 8.0..11.0),
            ),
            tierCount = 6, bestItemLevel = 80
        ),

        // ---------- SUFFIX: атрибуты ----------
        ModifierTemplate(
            code = "ADD_STRENGTH",
            name = "+# to Strength",
            source = SUFFIX,
            tags = listOf("attribute", "strength"),
            effects = listOf(effect(STOCK_STRENGTH, ADD, best = 50.0..54.0, worst = 8.0..12.0)),
            tierCount = 8, bestItemLevel = 82
        ),
        ModifierTemplate(
            code = "ADD_DEXTERITY",
            name = "+# to Dexterity",
            source = SUFFIX,
            tags = listOf("attribute", "dexterity"),
            effects = listOf(effect(STOCK_AGILITY, ADD, best = 50.0..54.0, worst = 8.0..12.0)),
            tierCount = 8, bestItemLevel = 82
        ),
        ModifierTemplate(
            code = "ADD_INTELLIGENCE",
            name = "+# to Intelligence",
            source = SUFFIX,
            tags = listOf("attribute", "intelligence"),
            effects = listOf(effect(STOCK_INTELLECT, ADD, best = 50.0..54.0, worst = 8.0..12.0)),
            tierCount = 8, bestItemLevel = 82
        ),

        // ---------- SUFFIX: составные атрибуты ----------
        ModifierTemplate(
            code = "ADD_STRENGTH_AND_DEXTERITY",
            name = "+# to Strength and Dexterity",
            source = SUFFIX,
            tags = listOf("attribute", "strength", "dexterity", "hybrid"),
            effects = listOf(
                effect(STOCK_STRENGTH, ADD, best = 20.0..24.0, worst = 5.0..8.0),
                effect(STOCK_AGILITY, ADD, best = 20.0..24.0, worst = 5.0..8.0),
            ),
            tierCount = 5, bestItemLevel = 80
        ),
        ModifierTemplate(
            code = "ADD_STRENGTH_AND_INTELLIGENCE",
            name = "+# to Strength and Intelligence",
            source = SUFFIX,
            tags = listOf("attribute", "strength", "intelligence", "hybrid"),
            effects = listOf(
                effect(STOCK_STRENGTH, ADD, best = 20.0..24.0, worst = 5.0..8.0),
                effect(STOCK_INTELLECT, ADD, best = 20.0..24.0, worst = 5.0..8.0),
            ),
            tierCount = 5, bestItemLevel = 80
        ),
        ModifierTemplate(
            code = "ADD_DEXTERITY_AND_INTELLIGENCE",
            name = "+# to Dexterity and Intelligence",
            source = SUFFIX,
            tags = listOf("attribute", "dexterity", "intelligence", "hybrid"),
            effects = listOf(
                effect(STOCK_AGILITY, ADD, best = 20.0..24.0, worst = 5.0..8.0),
                effect(STOCK_INTELLECT, ADD, best = 20.0..24.0, worst = 5.0..8.0),
            ),
            tierCount = 5, bestItemLevel = 80
        ),
        ModifierTemplate(
            code = "ADD_ALL_ATTRIBUTES",
            name = "+# to all Attributes",
            source = SUFFIX,
            tags = listOf("attribute", "hybrid"),
            effects = listOf(
                effect(STOCK_STRENGTH, ADD, best = 16.0..18.0, worst = 3.0..5.0),
                effect(STOCK_AGILITY, ADD, best = 16.0..18.0, worst = 3.0..5.0),
                effect(STOCK_INTELLECT, ADD, best = 16.0..18.0, worst = 3.0..5.0),
            ),
            tierCount = 5, bestItemLevel = 82
        ),

        // ---------- SUFFIX: сопротивления ----------
        ModifierTemplate(
            code = "ADD_FIRE_RESISTANCE",
            name = "+#% to Fire Resistance",
            source = SUFFIX,
            tags = listOf("resistance", "fire", "elemental"),
            effects = listOf(effect(STOCK_RESIST_FIRE, ADD, best = 46.0..48.0, worst = 6.0..11.0)),
            tierCount = 8, bestItemLevel = 84
        ),
        ModifierTemplate(
            code = "ADD_COLD_RESISTANCE",
            name = "+#% to Cold Resistance",
            source = SUFFIX,
            tags = listOf("resistance", "cold", "elemental"),
            effects = listOf(effect(STOCK_RESIST_COLD, ADD, best = 46.0..48.0, worst = 6.0..11.0)),
            tierCount = 8, bestItemLevel = 84
        ),
        ModifierTemplate(
            code = "ADD_LIGHTNING_RESISTANCE",
            name = "+#% to Lightning Resistance",
            source = SUFFIX,
            tags = listOf("resistance", "lightning", "elemental"),
            effects = listOf(effect(STOCK_RESIST_LIGHTNING, ADD, best = 46.0..48.0, worst = 6.0..11.0)),
            tierCount = 8, bestItemLevel = 84
        ),
        ModifierTemplate(
            code = "ADD_CHAOS_RESISTANCE",
            name = "+#% to Chaos Resistance",
            source = SUFFIX,
            tags = listOf("resistance", "chaos"),
            effects = listOf(effect(STOCK_RESIST_CHAOS, ADD, best = 31.0..35.0, worst = 5.0..10.0)),
            tierCount = 6, bestItemLevel = 81
        ),
        ModifierTemplate(
            code = "ADD_ALL_ELEMENTAL_RESISTANCES",
            name = "+#% to all Elemental Resistances",
            source = SUFFIX,
            tags = listOf("resistance", "elemental"),
            effects = listOf(effect(STOCK_RESIST_ALL, ADD, best = 15.0..16.0, worst = 3.0..5.0)),
            tierCount = 6, bestItemLevel = 84
        ),

        // ---------- SUFFIX: составные сопротивления ----------
        ModifierTemplate(
            code = "ADD_FIRE_AND_COLD_RESISTANCES",
            name = "+#% to Fire and Cold Resistances",
            source = SUFFIX,
            tags = listOf("resistance", "fire", "cold", "elemental", "hybrid"),
            effects = listOf(
                effect(STOCK_RESIST_FIRE, ADD, best = 22.0..24.0, worst = 5.0..8.0),
                effect(STOCK_RESIST_COLD, ADD, best = 22.0..24.0, worst = 5.0..8.0),
            ),
            tierCount = 5, bestItemLevel = 82
        ),
        ModifierTemplate(
            code = "ADD_FIRE_AND_LIGHTNING_RESISTANCES",
            name = "+#% to Fire and Lightning Resistances",
            source = SUFFIX,
            tags = listOf("resistance", "fire", "lightning", "elemental", "hybrid"),
            effects = listOf(
                effect(STOCK_RESIST_FIRE, ADD, best = 22.0..24.0, worst = 5.0..8.0),
                effect(STOCK_RESIST_LIGHTNING, ADD, best = 22.0..24.0, worst = 5.0..8.0),
            ),
            tierCount = 5, bestItemLevel = 82
        ),
        ModifierTemplate(
            code = "ADD_COLD_AND_LIGHTNING_RESISTANCES",
            name = "+#% to Cold and Lightning Resistances",
            source = SUFFIX,
            tags = listOf("resistance", "cold", "lightning", "elemental", "hybrid"),
            effects = listOf(
                effect(STOCK_RESIST_COLD, ADD, best = 22.0..24.0, worst = 5.0..8.0),
                effect(STOCK_RESIST_LIGHTNING, ADD, best = 22.0..24.0, worst = 5.0..8.0),
            ),
            tierCount = 5, bestItemLevel = 82
        ),

        // ---------- SUFFIX: скорость ----------
        ModifierTemplate(
            code = "INCREASED_ATTACK_SPEED",
            name = "#% increased Attack Speed",
            source = PASSIVE,
            tags = listOf("speed", "attack"),
            effects = listOf(effect(STOCK_ATTACK_SPEED, INCREASED, best = 25.0..27.0, worst = 5.0..7.0)),
            tierCount = 7, bestItemLevel = 77
        ),
        ModifierTemplate(
            code = "INCREASED_CAST_SPEED",
            name = "#% increased Cast Speed",
            source = SUFFIX,
            tags = listOf("speed", "caster"),
            effects = listOf(effect(STOCK_CAST_SPEED, INCREASED, best = 20.0..22.0, worst = 5.0..7.0)),
            tierCount = 7, bestItemLevel = 77
        ),
        ModifierTemplate(
            code = "INCREASED_MOVEMENT_SPEED",
            name = "#% increased Movement Speed",
            source = SUFFIX,
            tags = listOf("speed"),
            effects = listOf(effect(STOCK_MOVEMENT_SPEED, INCREASED, best = 30.0..34.0, worst = 10.0..14.0)),
            tierCount = 4, bestItemLevel = 86
        ),

        // ---------- SUFFIX: криты, реген, лич ----------
        ModifierTemplate(
            code = "INCREASED_CRITICAL_STRIKE_CHANCE",
            name = "#% increased Critical Strike Chance",
            source = SUFFIX,
            tags = listOf("critical"),
            effects = listOf(effect(STOCK_CRITICAL_CHANCE, INCREASED, best = 35.0..38.0, worst = 10.0..14.0)),
            tierCount = 7, bestItemLevel = 81
        ),
        ModifierTemplate(
            code = "ADD_CRITICAL_STRIKE_MULTIPLIER",
            name = "+#% to Critical Strike Multiplier",
            source = SUFFIX,
            tags = listOf("critical"),
            effects = listOf(effect(STOCK_CRITICAL_MULTIPLIER, ADD, best = 35.0..38.0, worst = 10.0..14.0)),
            tierCount = 7, bestItemLevel = 81
        ),
        ModifierTemplate(
            code = "ADD_LIFE_REGENERATION",
            name = "Regenerate # Life per second",
            source = SUFFIX,
            tags = listOf("life", "regen"),
            effects = listOf(effect(STOCK_HEALTH_REGEN, ADD, best = 20.0..25.0, worst = 1.0..2.0)),
            tierCount = 7, bestItemLevel = 84
        ),
        ModifierTemplate(
            code = "INCREASED_MANA_REGENERATION",
            name = "#% increased Mana Regeneration Rate",
            source = SUFFIX,
            tags = listOf("mana", "regen", "caster"),
            effects = listOf(effect(STOCK_MANA_REGEN, INCREASED, best = 60.0..69.0, worst = 10.0..19.0)),
            tierCount = 7, bestItemLevel = 80
        ),
        ModifierTemplate(
            code = "ADD_PHYSICAL_LIFE_LEECH",
            name = "#% of Physical Attack Damage Leeched as Life",
            source = SUFFIX,
            tags = listOf("leech", "physical", "attack"),
            effects = listOf(effect(STOCK_LEECH_PHYSICAL, ADD, best = 1.2..1.4, worst = 0.2..0.4)),
            tierCount = 4, bestItemLevel = 79
        ),
        ModifierTemplate(
            code = "ADD_BLOCK_CHANCE",
            name = "+#% Chance to Block",
            source = SUFFIX,
            tags = listOf("block", "defences"),
            effects = listOf(effect(STOCK_BLOCK_CHANCE, ADD, best = 5.0..6.0, worst = 1.0..2.0)),
            tierCount = 4, bestItemLevel = 80
        ),
        ModifierTemplate(
            code = "INCREASED_ITEM_RARITY",
            name = "#% increased Rarity of Items found",
            source = SUFFIX,
            tags = listOf("rarity"),
            effects = listOf(effect(STOCK_RARITY, INCREASED, best = 20.0..24.0, worst = 6.0..10.0)),
            tierCount = 5, bestItemLevel = 82
        ),

        // ---------- IMPLICIT: встроенные модификаторы базы ----------
        ModifierTemplate(
            code = "IMPLICIT_ADD_MAXIMUM_LIFE",
            name = "+# to maximum Life",
            source = IMPLICIT,
            tags = listOf("life", "implicit"),
            effects = listOf(effect(STOCK_HEALTH, ADD, best = 40.0..45.0, worst = 10.0..15.0)),
            tierCount = 3, bestItemLevel = 60
        ),
        ModifierTemplate(
            code = "IMPLICIT_ADD_ARMOUR",
            name = "+# to Armour",
            source = IMPLICIT,
            tags = listOf("armour", "defences", "implicit"),
            effects = listOf(effect(STOCK_ARMOR, ADD, best = 100.0..120.0, worst = 20.0..30.0)),
            tierCount = 3, bestItemLevel = 60
        ),
        ModifierTemplate(
            code = "IMPLICIT_ADD_ALL_ELEMENTAL_RESISTANCES",
            name = "+#% to all Elemental Resistances",
            source = IMPLICIT,
            tags = listOf("resistance", "elemental", "implicit"),
            effects = listOf(effect(STOCK_RESIST_ALL, ADD, best = 12.0..16.0, worst = 4.0..6.0)),
            tierCount = 3, bestItemLevel = 60
        ),
        ModifierTemplate(
            code = "IMPLICIT_ADD_ALL_ATTRIBUTES",
            name = "+# to all Attributes",
            source = IMPLICIT,
            tags = listOf("attribute", "implicit", "hybrid"),
            effects = listOf(
                effect(STOCK_STRENGTH, ADD, best = 12.0..16.0, worst = 4.0..6.0),
                effect(STOCK_AGILITY, ADD, best = 12.0..16.0, worst = 4.0..6.0),
                effect(STOCK_INTELLECT, ADD, best = 12.0..16.0, worst = 4.0..6.0),
            ),
            tierCount = 3, bestItemLevel = 60
        ),

        // ---------- ENCHANTMENT: лабиринтные энчанты ----------
        ModifierTemplate(
            code = "ENCHANT_INCREASED_MOVEMENT_SPEED",
            name = "#% increased Movement Speed",
            source = ENCHANTMENT,
            tags = listOf("speed", "enchantment"),
            effects = listOf(effect(STOCK_MOVEMENT_SPEED, INCREASED, best = 16.0..16.0, worst = 8.0..8.0)),
            tierCount = 3, bestItemLevel = 68
        ),
        ModifierTemplate(
            code = "ENCHANT_INCREASED_AURA_EFFECT",
            name = "#% increased effect of Auras",
            source = ENCHANTMENT,
            tags = listOf("aura", "enchantment"),
            effects = listOf(effect(STOCK_AURA_EFFECT, INCREASED, best = 12.0..12.0, worst = 4.0..4.0)),
            tierCount = 3, bestItemLevel = 68
        ),

        // ---------- CORRUPTION: vaal-модификаторы ----------
        ModifierTemplate(
            code = "CORRUPTED_ADD_ALL_ELEMENTAL_RESISTANCES",
            name = "+#% to all Elemental Resistances",
            source = CORRUPTION,
            tags = listOf("resistance", "elemental", "corrupted"),
            effects = listOf(effect(STOCK_RESIST_ALL, ADD, best = 10.0..12.0, worst = 5.0..7.0)),
            tierCount = 2, bestItemLevel = 68
        ),
        ModifierTemplate(
            code = "CORRUPTED_MORE_PHYSICAL_DAMAGE",
            name = "#% more Physical Damage",
            source = CORRUPTION,
            tags = listOf("physical", "damage", "corrupted"),
            effects = listOf(effect(STOCK_ATTACK_PHYSICAL, MORE, best = 12.0..15.0, worst = 5.0..8.0)),
            tierCount = 2, bestItemLevel = 68
        ),
        // ---------- LOCAL: база предметов и её проценты ----------
        // Как в POE, эти модификаторы считаются внутри своего предмета:
        // процент брони на нагруднике умножает броню нагрудника, а не персонажа.
        ModifierTemplate(
            code = "IMPLICIT_ARMOUR_BASE",
            name = "+# to Armour",
            source = IMPLICIT, isLocal = true,
            tags = listOf("armour", "defences", "implicit", "base"),
            effects = listOf(effect(STOCK_ARMOR, ADD, best = 1.0..1.0)),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "IMPLICIT_EVASION_BASE",
            name = "+# to Evasion Rating",
            source = IMPLICIT, isLocal = true,
            tags = listOf("evasion", "defences", "implicit", "base"),
            effects = listOf(effect(STOCK_EVASION, ADD, best = 1.0..1.0)),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "IMPLICIT_ENERGY_SHIELD_BASE",
            name = "+# to maximum Energy Shield",
            source = IMPLICIT, isLocal = true,
            tags = listOf("energy_shield", "defences", "implicit", "base"),
            effects = listOf(effect(STOCK_ENERGY_SHIELD, ADD, best = 1.0..1.0)),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "IMPLICIT_PHYSICAL_DAMAGE_BASE",
            name = "Adds # Physical Damage",
            source = IMPLICIT, isLocal = true,
            tags = listOf("physical", "damage", "attack", "implicit", "base"),
            effects = listOf(effect(STOCK_ATTACK_PHYSICAL, ADD, best = 1.0..1.0)),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "IMPLICIT_ATTACK_SPEED_BASE",
            name = "Attacks per Second: #",
            source = IMPLICIT, isLocal = true,
            tags = listOf("speed", "attack", "implicit", "base"),
            effects = listOf(effect(STOCK_ATTACK_SPEED, ADD, best = 1.0..1.0)),
            tierCount = 1, bestItemLevel = 1
        ),

        ModifierTemplate(
            code = "LOCAL_ADD_ARMOUR",
            name = "+# to Armour",
            source = PREFIX, isLocal = true,
            tags = listOf("armour", "defences"),
            effects = listOf(effect(STOCK_ARMOR, ADD, best = 380.0..440.0, worst = 8.0..15.0)),
            tierCount = 8, bestItemLevel = 84
        ),
        ModifierTemplate(
            code = "LOCAL_INCREASED_ARMOUR",
            name = "#% increased Armour",
            source = PREFIX, isLocal = true,
            tags = listOf("armour", "defences"),
            effects = listOf(effect(STOCK_ARMOR, INCREASED, best = 100.0..109.0, worst = 6.0..13.0)),
            tierCount = 8, bestItemLevel = 84
        ),
        ModifierTemplate(
            code = "LOCAL_ADD_EVASION_RATING",
            name = "+# to Evasion Rating",
            source = PREFIX, isLocal = true,
            tags = listOf("evasion", "defences"),
            effects = listOf(effect(STOCK_EVASION, ADD, best = 380.0..440.0, worst = 8.0..15.0)),
            tierCount = 8, bestItemLevel = 84
        ),
        ModifierTemplate(
            code = "LOCAL_INCREASED_EVASION_RATING",
            name = "#% increased Evasion Rating",
            source = PREFIX, isLocal = true,
            tags = listOf("evasion", "defences"),
            effects = listOf(effect(STOCK_EVASION, INCREASED, best = 100.0..109.0, worst = 6.0..13.0)),
            tierCount = 8, bestItemLevel = 84
        ),
        ModifierTemplate(
            code = "LOCAL_ADD_ENERGY_SHIELD",
            name = "+# to maximum Energy Shield",
            source = PREFIX, isLocal = true,
            tags = listOf("energy_shield", "defences"),
            effects = listOf(effect(STOCK_ENERGY_SHIELD, ADD, best = 65.0..71.0, worst = 3.0..5.0)),
            tierCount = 8, bestItemLevel = 86
        ),
        ModifierTemplate(
            code = "LOCAL_INCREASED_ENERGY_SHIELD",
            name = "#% increased maximum Energy Shield",
            source = PREFIX, isLocal = true,
            tags = listOf("energy_shield", "defences"),
            effects = listOf(effect(STOCK_ENERGY_SHIELD, INCREASED, best = 100.0..109.0, worst = 6.0..13.0)),
            tierCount = 8, bestItemLevel = 84
        ),
        ModifierTemplate(
            code = "LOCAL_INCREASED_ARMOUR_AND_EVASION",
            name = "#% increased Armour and Evasion",
            source = PREFIX, isLocal = true,
            tags = listOf("armour", "evasion", "defences", "hybrid"),
            effects = listOf(
                effect(STOCK_ARMOR, INCREASED, best = 82.0..90.0, worst = 6.0..13.0),
                effect(STOCK_EVASION, INCREASED, best = 82.0..90.0, worst = 6.0..13.0),
            ),
            tierCount = 8, bestItemLevel = 84
        ),
        ModifierTemplate(
            code = "LOCAL_INCREASED_ARMOUR_AND_ENERGY_SHIELD",
            name = "#% increased Armour and Energy Shield",
            source = PREFIX, isLocal = true,
            tags = listOf("armour", "energy_shield", "defences", "hybrid"),
            effects = listOf(
                effect(STOCK_ARMOR, INCREASED, best = 82.0..90.0, worst = 6.0..13.0),
                effect(STOCK_ENERGY_SHIELD, INCREASED, best = 82.0..90.0, worst = 6.0..13.0),
            ),
            tierCount = 8, bestItemLevel = 84
        ),
        ModifierTemplate(
            code = "LOCAL_INCREASED_EVASION_AND_ENERGY_SHIELD",
            name = "#% increased Evasion and Energy Shield",
            source = PREFIX, isLocal = true,
            tags = listOf("evasion", "energy_shield", "defences", "hybrid"),
            effects = listOf(
                effect(STOCK_EVASION, INCREASED, best = 82.0..90.0, worst = 6.0..13.0),
                effect(STOCK_ENERGY_SHIELD, INCREASED, best = 82.0..90.0, worst = 6.0..13.0),
            ),
            tierCount = 8, bestItemLevel = 84
        ),
        ModifierTemplate(
            code = "LOCAL_ADD_PHYSICAL_DAMAGE",
            name = "Adds # Physical Damage",
            source = PREFIX, isLocal = true,
            tags = listOf("physical", "damage", "attack"),
            effects = listOf(effect(STOCK_ATTACK_PHYSICAL, ADD, best = 25.0..45.0, worst = 1.0..3.0)),
            tierCount = 8, bestItemLevel = 77
        ),
        ModifierTemplate(
            code = "LOCAL_INCREASED_PHYSICAL_DAMAGE",
            name = "#% increased Physical Damage",
            source = PREFIX, isLocal = true,
            tags = listOf("physical", "damage", "attack"),
            effects = listOf(effect(STOCK_ATTACK_PHYSICAL, INCREASED, best = 170.0..179.0, worst = 40.0..49.0)),
            tierCount = 8, bestItemLevel = 81
        ),
        ModifierTemplate(
            code = "LOCAL_INCREASED_ATTACK_SPEED",
            name = "#% increased Attack Speed",
            source = SUFFIX, isLocal = true,
            tags = listOf("speed", "attack"),
            effects = listOf(effect(STOCK_ATTACK_SPEED, INCREASED, best = 25.0..27.0, worst = 5.0..7.0)),
            tierCount = 7, bestItemLevel = 77
        ),

        // ---------- CONVERSION: атрибуты в производные характеристики ----------
        // Источник конверсии обязан иметь меньший order, чем приёмник,
        // поэтому цикл здесь невыразим.
        ModifierTemplate(
            code = "CONVERT_STRENGTH_TO_LIFE",
            name = "+# to maximum Life per # Strength",
            source = PASSIVE,
            tags = listOf("life", "attribute", "conversion"),
            effects = listOf(
                conversion(STOCK_HEALTH, ADD, perStat = STOCK_STRENGTH, perAmount = 2.0)
            ),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "CONVERT_DEXTERITY_TO_EVASION",
            name = "+# to Evasion Rating per # Dexterity",
            source = PASSIVE,
            tags = listOf("evasion", "attribute", "conversion"),
            effects = listOf(
                conversion(STOCK_EVASION, ADD, perStat = STOCK_AGILITY, perAmount = 1.0)
            ),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "CONVERT_INTELLIGENCE_TO_MANA",
            name = "+# to maximum Mana per # Intelligence",
            source = PASSIVE,
            tags = listOf("mana", "attribute", "conversion"),
            effects = listOf(
                conversion(STOCK_MANA, ADD, perStat = STOCK_INTELLECT, perAmount = 2.0)
            ),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "CONVERT_INTELLIGENCE_TO_ENERGY_SHIELD",
            name = "#% increased Energy Shield per # Intelligence",
            source = PASSIVE,
            tags = listOf("energy_shield", "attribute", "conversion"),
            effects = listOf(
                conversion(STOCK_ENERGY_SHIELD, INCREASED, perStat = STOCK_INTELLECT, perAmount = 5.0)
            ),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "CONVERT_STRENGTH_TO_PHYSICAL_DAMAGE",
            name = "#% increased Physical Damage per # Strength",
            source = PASSIVE,
            tags = listOf("physical", "damage", "attribute", "conversion"),
            effects = listOf(
                conversion(STOCK_ATTACK_PHYSICAL, INCREASED, perStat = STOCK_STRENGTH, perAmount = 5.0)
            ),
            tierCount = 1, bestItemLevel = 1
        ),

        // ---------- PASSIVE: то, что даёт только дерево навыков ----------
        // Значения приходят от узла дерева, тир здесь чисто формальный.
        ModifierTemplate(
            code = "PASSIVE_SET_CRITICAL_STRIKE_CHANCE",
            name = "Your Critical Strike Chance is #%",
            source = PASSIVE,
            tags = listOf("critical", "passive"),
            effects = listOf(effect(STOCK_CRITICAL_CHANCE, SET, best = 0.0..0.0)),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "PASSIVE_SET_MAXIMUM_LIFE",
            name = "Your Maximum Life is #",
            source = PASSIVE,
            tags = listOf("life", "passive"),
            effects = listOf(effect(STOCK_HEALTH, SET, best = 1.0..1.0)),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "PASSIVE_SET_MAXIMUM_MANA",
            name = "Your Maximum Mana is #",
            source = PASSIVE,
            tags = listOf("mana", "passive"),
            effects = listOf(effect(STOCK_MANA, SET, best = 0.0..0.0)),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "PASSIVE_SET_EVASION_RATING",
            name = "Your Evasion Rating is #",
            source = PASSIVE,
            tags = listOf("evasion", "defences", "passive"),
            effects = listOf(effect(STOCK_EVASION, SET, best = 0.0..0.0)),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "PASSIVE_SET_CHAOS_RESISTANCE",
            name = "Your Chaos Resistance is #%",
            source = PASSIVE,
            tags = listOf("resistance", "chaos", "passive"),
            effects = listOf(effect(STOCK_RESIST_CHAOS, SET, best = 100.0..100.0)),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "PASSIVE_MORE_ARMOUR",
            name = "#% more Armour",
            source = PASSIVE,
            tags = listOf("armour", "defences", "passive"),
            effects = listOf(effect(STOCK_ARMOR, MORE, best = 100.0..100.0)),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "PASSIVE_MORE_EVASION_RATING",
            name = "#% more Evasion Rating",
            source = PASSIVE,
            tags = listOf("evasion", "defences", "passive"),
            effects = listOf(effect(STOCK_EVASION, MORE, best = 30.0..30.0)),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "PASSIVE_MORE_MAXIMUM_LIFE",
            name = "#% more maximum Life",
            source = PASSIVE,
            tags = listOf("life", "passive"),
            effects = listOf(effect(STOCK_HEALTH, MORE, best = 20.0..20.0)),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "PASSIVE_MORE_PHYSICAL_DAMAGE",
            name = "#% more Physical Damage",
            source = PASSIVE,
            tags = listOf("physical", "damage", "passive"),
            effects = listOf(effect(STOCK_ATTACK_PHYSICAL, MORE, best = 20.0..20.0)),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "PASSIVE_MORE_STUN_THRESHOLD",
            name = "#% more Stun Threshold",
            source = PASSIVE,
            tags = listOf("defences", "passive"),
            effects = listOf(effect(STOCK_STUN_THRESHOLD, MORE, best = 100.0..100.0)),
            tierCount = 1, bestItemLevel = 1
        ),

        ModifierTemplate(
            code = "CORRUPTED_SET_CRITICAL_STRIKE_CHANCE",
            name = "Your Critical Strike Chance is #%",
            source = CORRUPTION,
            tags = listOf("critical", "corrupted"),
            effects = listOf(effect(STOCK_CRITICAL_CHANCE, SET, best = 5.0..5.0)),
            tierCount = 1, bestItemLevel = 68
        ),
    )

    // ==================== Генерация документов ====================

    /**
     * Документы коллекции `ModifierDefinition`.
     */
    fun seedDefinitions(): List<ModifierDefinition> = templates.toDefinitions()

    /**
     * Документы коллекции `ModifierTier` для уже сохранённых описаний.
     *
     * @param definitions описания, которым принадлежат тиры (нужны их _id)
     */
    fun seedTiers(definitions: List<ModifierDefinition>): List<ModifierTier> = templates.toTiers(definitions)
}

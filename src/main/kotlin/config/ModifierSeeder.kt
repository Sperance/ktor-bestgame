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
import application.enums.EnumStatStock.STOCK_CRITICAL_DAMAGE
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
import application.enums.EnumStatStock.STOCK_GOLD
import application.enums.EnumStatStock.STOCK_RARITY
import application.enums.EnumStatStock.STOCK_RESIST_ALL
import application.enums.EnumStatStock.STOCK_RESIST_CHAOS
import application.enums.EnumStatStock.STOCK_RESIST_COLD
import application.enums.EnumStatStock.STOCK_RESIST_FIRE
import application.enums.EnumStatStock.STOCK_RESIST_LIGHTNING
import application.enums.EnumStatStock.STOCK_STRENGTH
import application.enums.EnumStatStock.STOCK_STUN_THRESHOLD
import application.enums.EnumStatStock.STOCK_EXPERIENCE
import application.enums.EnumStatStock.STOCK_ENERGY
import application.enums.EnumStatStock.STOCK_ENERGY_REGEN
import application.enums.EnumStatStock.STOCK_QUANTITY
import application.enums.EnumStatStock.STOCK_LEECH_MAGICAL
import application.enums.EnumStatStock.STOCK_LEECH_ALL
import application.enums.EnumStatStock.STOCK_CONSTITUTION
import application.enums.EnumStatStock.STOCK_CAST_STRENGTH
import application.enums.EnumStatStock.STOCK_CURSE_EFFECT
import application.enums.EnumInfluence.ELDER
import application.enums.EnumInfluence.SHAPER
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
            source = PREFIX,
            tags = listOf("life", "defences"),
            effects = listOf(effect(STOCK_HEALTH, ADD, best = 120.0..129.0, worst = 10.0..19.0)),
            tierCount = 8, bestItemLevel = 86
        ),
        ModifierTemplate(
            code = "ADD_MAXIMUM_MANA",
            source = PREFIX,
            tags = listOf("mana", "caster"),
            effects = listOf(effect(STOCK_MANA, ADD, best = 129.0..142.0, worst = 15.0..19.0)),
            tierCount = 8, bestItemLevel = 85
        ),
        ModifierTemplate(
            code = "ADD_ENERGY_SHIELD",
            source = PASSIVE,
            tags = listOf("energy_shield", "defences"),
            effects = listOf(effect(STOCK_ENERGY_SHIELD, ADD, best = 65.0..71.0, worst = 3.0..5.0)),
            tierCount = 8, bestItemLevel = 86
        ),

        // ---------- PREFIX: защита ----------
        ModifierTemplate(
            code = "ADD_ARMOUR",
            source = PASSIVE,
            tags = listOf("armour", "defences"),
            effects = listOf(effect(STOCK_ARMOR, ADD, best = 380.0..440.0, worst = 8.0..15.0)),
            tierCount = 8, bestItemLevel = 84
        ),
        ModifierTemplate(
            code = "INCREASED_ARMOUR",
            source = PASSIVE,
            tags = listOf("armour", "defences"),
            effects = listOf(effect(STOCK_ARMOR, INCREASED, best = 100.0..109.0, worst = 6.0..13.0)),
            tierCount = 8, bestItemLevel = 84
        ),
        ModifierTemplate(
            code = "ADD_EVASION_RATING",
            source = PASSIVE,
            tags = listOf("evasion", "defences"),
            effects = listOf(effect(STOCK_EVASION, ADD, best = 380.0..440.0, worst = 8.0..15.0)),
            tierCount = 8, bestItemLevel = 84
        ),
        ModifierTemplate(
            code = "INCREASED_EVASION_RATING",
            source = PASSIVE,
            tags = listOf("evasion", "defences"),
            effects = listOf(effect(STOCK_EVASION, INCREASED, best = 100.0..109.0, worst = 6.0..13.0)),
            tierCount = 8, bestItemLevel = 84
        ),
        ModifierTemplate(
            code = "INCREASED_ENERGY_SHIELD",
            source = PASSIVE,
            tags = listOf("energy_shield", "defences"),
            effects = listOf(effect(STOCK_ENERGY_SHIELD, INCREASED, best = 100.0..109.0, worst = 6.0..13.0)),
            tierCount = 8, bestItemLevel = 84
        ),

        // ---------- PREFIX: урон ----------
        ModifierTemplate(
            code = "ADD_PHYSICAL_DAMAGE",
            source = PASSIVE,
            tags = listOf("physical", "damage", "attack"),
            effects = listOf(effect(STOCK_ATTACK_PHYSICAL, ADD, best = 25.0..45.0, worst = 1.0..3.0)),
            tierCount = 8, bestItemLevel = 77
        ),
        ModifierTemplate(
            code = "INCREASED_PHYSICAL_DAMAGE",
            source = PASSIVE,
            tags = listOf("physical", "damage", "attack"),
            effects = listOf(effect(STOCK_ATTACK_PHYSICAL, INCREASED, best = 170.0..179.0, worst = 40.0..49.0)),
            tierCount = 8, bestItemLevel = 81
        ),
        ModifierTemplate(
            code = "ADD_FIRE_DAMAGE",
            source = PREFIX,
            tags = listOf("fire", "elemental", "damage", "attack"),
            effects = listOf(effect(STOCK_ATTACK_FIRE, ADD, best = 30.0..55.0, worst = 1.0..3.0)),
            tierCount = 8, bestItemLevel = 82
        ),
        ModifierTemplate(
            code = "ADD_COLD_DAMAGE",
            source = PREFIX,
            tags = listOf("cold", "elemental", "damage", "attack"),
            effects = listOf(effect(STOCK_ATTACK_COLD, ADD, best = 27.0..50.0, worst = 1.0..3.0)),
            tierCount = 8, bestItemLevel = 81
        ),
        ModifierTemplate(
            code = "ADD_LIGHTNING_DAMAGE",
            source = PREFIX,
            tags = listOf("lightning", "elemental", "damage", "attack"),
            effects = listOf(effect(STOCK_ATTACK_LIGHTNING, ADD, best = 2.0..90.0, worst = 1.0..5.0)),
            tierCount = 8, bestItemLevel = 83
        ),
        ModifierTemplate(
            code = "ADD_CHAOS_DAMAGE",
            source = PREFIX,
            tags = listOf("chaos", "damage", "attack"),
            effects = listOf(effect(STOCK_ATTACK_CHAOS, ADD, best = 20.0..38.0, worst = 2.0..4.0)),
            tierCount = 6, bestItemLevel = 80
        ),
        ModifierTemplate(
            code = "INCREASED_SPELL_DAMAGE",
            source = PREFIX,
            tags = listOf("caster", "damage"),
            effects = listOf(effect(STOCK_ATTACK_MAGICAL, INCREASED, best = 90.0..104.0, worst = 10.0..14.0)),
            tierCount = 8, bestItemLevel = 84
        ),

        // ---------- PREFIX: составные ----------
        ModifierTemplate(
            code = "ADD_MAXIMUM_LIFE_AND_MANA",
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
            source = SUFFIX,
            tags = listOf("attribute", "strength"),
            effects = listOf(effect(STOCK_STRENGTH, ADD, best = 50.0..54.0, worst = 8.0..12.0)),
            tierCount = 8, bestItemLevel = 82
        ),
        ModifierTemplate(
            code = "ADD_DEXTERITY",
            source = SUFFIX,
            tags = listOf("attribute", "dexterity"),
            effects = listOf(effect(STOCK_AGILITY, ADD, best = 50.0..54.0, worst = 8.0..12.0)),
            tierCount = 8, bestItemLevel = 82
        ),
        ModifierTemplate(
            code = "ADD_INTELLIGENCE",
            source = SUFFIX,
            tags = listOf("attribute", "intelligence"),
            effects = listOf(effect(STOCK_INTELLECT, ADD, best = 50.0..54.0, worst = 8.0..12.0)),
            tierCount = 8, bestItemLevel = 82
        ),

        // ---------- SUFFIX: составные атрибуты ----------
        ModifierTemplate(
            code = "ADD_STRENGTH_AND_DEXTERITY",
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
            source = SUFFIX,
            tags = listOf("resistance", "fire", "elemental"),
            effects = listOf(effect(STOCK_RESIST_FIRE, ADD, best = 46.0..48.0, worst = 6.0..11.0)),
            tierCount = 8, bestItemLevel = 84
        ),
        ModifierTemplate(
            code = "ADD_COLD_RESISTANCE",
            source = SUFFIX,
            tags = listOf("resistance", "cold", "elemental"),
            effects = listOf(effect(STOCK_RESIST_COLD, ADD, best = 46.0..48.0, worst = 6.0..11.0)),
            tierCount = 8, bestItemLevel = 84
        ),
        ModifierTemplate(
            code = "ADD_LIGHTNING_RESISTANCE",
            source = SUFFIX,
            tags = listOf("resistance", "lightning", "elemental"),
            effects = listOf(effect(STOCK_RESIST_LIGHTNING, ADD, best = 46.0..48.0, worst = 6.0..11.0)),
            tierCount = 8, bestItemLevel = 84
        ),
        ModifierTemplate(
            code = "ADD_CHAOS_RESISTANCE",
            source = SUFFIX,
            tags = listOf("resistance", "chaos"),
            effects = listOf(effect(STOCK_RESIST_CHAOS, ADD, best = 31.0..35.0, worst = 5.0..10.0)),
            tierCount = 6, bestItemLevel = 81
        ),
        ModifierTemplate(
            code = "ADD_ALL_ELEMENTAL_RESISTANCES",
            source = SUFFIX,
            tags = listOf("resistance", "elemental"),
            effects = listOf(effect(STOCK_RESIST_ALL, ADD, best = 15.0..16.0, worst = 3.0..5.0)),
            tierCount = 6, bestItemLevel = 84
        ),

        // ---------- SUFFIX: составные сопротивления ----------
        ModifierTemplate(
            code = "ADD_FIRE_AND_COLD_RESISTANCES",
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
            source = PASSIVE,
            tags = listOf("speed", "attack"),
            effects = listOf(effect(STOCK_ATTACK_SPEED, INCREASED, best = 25.0..27.0, worst = 5.0..7.0)),
            tierCount = 7, bestItemLevel = 77
        ),
        ModifierTemplate(
            code = "INCREASED_CAST_SPEED",
            source = SUFFIX,
            tags = listOf("speed", "caster"),
            effects = listOf(effect(STOCK_CAST_SPEED, INCREASED, best = 20.0..22.0, worst = 5.0..7.0)),
            tierCount = 7, bestItemLevel = 77
        ),
        ModifierTemplate(
            code = "INCREASED_MOVEMENT_SPEED",
            source = SUFFIX,
            tags = listOf("speed"),
            effects = listOf(effect(STOCK_MOVEMENT_SPEED, INCREASED, best = 30.0..34.0, worst = 10.0..14.0)),
            tierCount = 4, bestItemLevel = 86
        ),

        // ---------- SUFFIX: криты, реген, лич ----------
        ModifierTemplate(
            code = "INCREASED_CRITICAL_STRIKE_CHANCE",
            source = SUFFIX,
            tags = listOf("critical"),
            effects = listOf(effect(STOCK_CRITICAL_CHANCE, INCREASED, best = 35.0..38.0, worst = 10.0..14.0)),
            tierCount = 7, bestItemLevel = 81
        ),
        ModifierTemplate(
            code = "ADD_CRITICAL_STRIKE_MULTIPLIER",
            source = SUFFIX,
            tags = listOf("critical"),
            effects = listOf(effect(STOCK_CRITICAL_MULTIPLIER, ADD, best = 35.0..38.0, worst = 10.0..14.0)),
            tierCount = 7, bestItemLevel = 81
        ),
        ModifierTemplate(
            code = "ADD_LIFE_REGENERATION",
            source = SUFFIX,
            tags = listOf("life", "regen"),
            effects = listOf(effect(STOCK_HEALTH_REGEN, ADD, best = 20.0..25.0, worst = 1.0..2.0)),
            tierCount = 7, bestItemLevel = 84
        ),
        ModifierTemplate(
            code = "INCREASED_MANA_REGENERATION",
            source = SUFFIX,
            tags = listOf("mana", "regen", "caster"),
            effects = listOf(effect(STOCK_MANA_REGEN, INCREASED, best = 60.0..69.0, worst = 10.0..19.0)),
            tierCount = 7, bestItemLevel = 80
        ),
        ModifierTemplate(
            code = "ADD_PHYSICAL_LIFE_LEECH",
            source = SUFFIX,
            tags = listOf("leech", "physical", "attack"),
            effects = listOf(effect(STOCK_LEECH_PHYSICAL, ADD, best = 1.2..1.4, worst = 0.2..0.4)),
            tierCount = 4, bestItemLevel = 79
        ),
        ModifierTemplate(
            code = "ADD_BLOCK_CHANCE",
            source = SUFFIX,
            tags = listOf("block", "defences"),
            effects = listOf(effect(STOCK_BLOCK_CHANCE, ADD, best = 5.0..6.0, worst = 1.0..2.0)),
            tierCount = 4, bestItemLevel = 80
        ),
        ModifierTemplate(
            code = "INCREASED_ITEM_RARITY",
            source = SUFFIX,
            tags = listOf("rarity"),
            effects = listOf(effect(STOCK_RARITY, INCREASED, best = 20.0..24.0, worst = 6.0..10.0)),
            tierCount = 5, bestItemLevel = 82
        ),
        // Единственный источник STOCK_GOLD в мире. До него характеристика существовала,
        // участвовала множителем в цене продажи и всегда равнялась нулю - то есть
        // торговец платил по базе и делал вид, что считает.
        ModifierTemplate(
            code = "INCREASED_SELL_VALUE",
            source = SUFFIX,
            tags = listOf("gold"),
            effects = listOf(effect(STOCK_GOLD, INCREASED, best = 25.0..30.0, worst = 8.0..12.0)),
            tierCount = 4, bestItemLevel = 78
        ),

        // ---------- PREFIX: новые аффиксы 0.23.0 ----------
        ModifierTemplate(
            code = "LOCAL_ADD_ARMOUR_AND_EVASION",
            source = PREFIX, isLocal = true,
            tags = listOf("armour", "evasion", "defences", "hybrid"),
            effects = listOf(
                effect(STOCK_ARMOR, ADD, best = 90.0..120.0, worst = 6.0..10.0),
                effect(STOCK_EVASION, ADD, best = 90.0..120.0, worst = 6.0..10.0),
            ),
            tierCount = 6, bestItemLevel = 83
        ),
        ModifierTemplate(
            code = "LOCAL_ADD_ARMOUR_AND_ENERGY_SHIELD",
            source = PREFIX, isLocal = true,
            tags = listOf("armour", "energy_shield", "defences", "hybrid"),
            effects = listOf(
                effect(STOCK_ARMOR, ADD, best = 90.0..120.0, worst = 6.0..10.0),
                effect(STOCK_ENERGY_SHIELD, ADD, best = 30.0..40.0, worst = 3.0..5.0),
            ),
            tierCount = 6, bestItemLevel = 83
        ),
        ModifierTemplate(
            code = "LOCAL_ADD_EVASION_AND_ENERGY_SHIELD",
            source = PREFIX, isLocal = true,
            tags = listOf("evasion", "energy_shield", "defences", "hybrid"),
            effects = listOf(
                effect(STOCK_EVASION, ADD, best = 90.0..120.0, worst = 6.0..10.0),
                effect(STOCK_ENERGY_SHIELD, ADD, best = 30.0..40.0, worst = 3.0..5.0),
            ),
            tierCount = 6, bestItemLevel = 83
        ),
        ModifierTemplate(
            code = "ADD_STUN_THRESHOLD",
            source = PREFIX,
            tags = listOf("stun", "defences"),
            effects = listOf(effect(STOCK_STUN_THRESHOLD, ADD, best = 200.0..250.0, worst = 20.0..40.0)),
            tierCount = 6, bestItemLevel = 80
        ),
        ModifierTemplate(
            code = "ADD_MAXIMUM_LIFE_AND_LIFE_REGENERATION",
            source = PREFIX,
            tags = listOf("life", "regen", "hybrid"),
            effects = listOf(
                effect(STOCK_HEALTH, ADD, best = 40.0..49.0, worst = 8.0..12.0),
                effect(STOCK_HEALTH_REGEN, ADD, best = 8.0..10.0, worst = 1.0..2.0),
            ),
            tierCount = 5, bestItemLevel = 78
        ),
        ModifierTemplate(
            code = "ADD_MAXIMUM_MANA_AND_MANA_REGENERATION",
            source = PREFIX,
            tags = listOf("mana", "regen", "hybrid"),
            effects = listOf(
                effect(STOCK_MANA, ADD, best = 45.0..54.0, worst = 10.0..14.0),
                effect(STOCK_MANA_REGEN, INCREASED, best = 20.0..25.0, worst = 5.0..8.0),
            ),
            tierCount = 5, bestItemLevel = 78
        ),
        ModifierTemplate(
            code = "INCREASED_EXPERIENCE_GAIN",
            source = PREFIX,
            tags = listOf("experience"),
            effects = listOf(effect(STOCK_EXPERIENCE, INCREASED, best = 5.0..6.0, worst = 1.0..2.0)),
            tierCount = 3, bestItemLevel = 75, weight = 250
        ),
        ModifierTemplate(
            code = "ADD_MAXIMUM_ENERGY",
            source = PREFIX,
            tags = listOf("energy"),
            effects = listOf(effect(STOCK_ENERGY, ADD, best = 35.0..40.0, worst = 5.0..9.0)),
            tierCount = 6, bestItemLevel = 80
        ),

        // ---------- SUFFIX: новые аффиксы 0.23.0 ----------
        ModifierTemplate(
            code = "ADD_FIRE_AND_CHAOS_RESISTANCES",
            source = SUFFIX,
            tags = listOf("resistance", "fire", "chaos", "hybrid"),
            effects = listOf(
                effect(STOCK_RESIST_FIRE, ADD, best = 16.0..20.0, worst = 5.0..8.0),
                effect(STOCK_RESIST_CHAOS, ADD, best = 16.0..20.0, worst = 5.0..8.0),
            ),
            tierCount = 5, bestItemLevel = 81, weight = 500
        ),
        ModifierTemplate(
            code = "ADD_COLD_AND_CHAOS_RESISTANCES",
            source = SUFFIX,
            tags = listOf("resistance", "cold", "chaos", "hybrid"),
            effects = listOf(
                effect(STOCK_RESIST_COLD, ADD, best = 16.0..20.0, worst = 5.0..8.0),
                effect(STOCK_RESIST_CHAOS, ADD, best = 16.0..20.0, worst = 5.0..8.0),
            ),
            tierCount = 5, bestItemLevel = 81, weight = 500
        ),
        ModifierTemplate(
            code = "ADD_LIGHTNING_AND_CHAOS_RESISTANCES",
            source = SUFFIX,
            tags = listOf("resistance", "lightning", "chaos", "hybrid"),
            effects = listOf(
                effect(STOCK_RESIST_LIGHTNING, ADD, best = 16.0..20.0, worst = 5.0..8.0),
                effect(STOCK_RESIST_CHAOS, ADD, best = 16.0..20.0, worst = 5.0..8.0),
            ),
            tierCount = 5, bestItemLevel = 81, weight = 500
        ),
        ModifierTemplate(
            code = "ADD_ALL_RESISTANCES",
            source = SUFFIX,
            tags = listOf("resistance", "chaos", "elemental", "hybrid"),
            effects = listOf(
                effect(STOCK_RESIST_ALL, ADD, best = 8.0..10.0, worst = 3.0..4.0),
                effect(STOCK_RESIST_CHAOS, ADD, best = 8.0..10.0, worst = 3.0..4.0),
            ),
            tierCount = 4, bestItemLevel = 84, weight = 250
        ),
        ModifierTemplate(
            code = "ADD_MANA_REGENERATION",
            source = SUFFIX,
            tags = listOf("mana", "regen"),
            effects = listOf(effect(STOCK_MANA_REGEN, ADD, best = 6.0..8.0, worst = 1.0..2.0)),
            tierCount = 5, bestItemLevel = 76
        ),
        ModifierTemplate(
            code = "INCREASED_LIFE_REGENERATION",
            source = SUFFIX,
            tags = listOf("life", "regen"),
            effects = listOf(effect(STOCK_HEALTH_REGEN, INCREASED, best = 15.0..20.0, worst = 3.0..5.0)),
            tierCount = 5, bestItemLevel = 78
        ),
        ModifierTemplate(
            code = "INCREASED_ENERGY_REGENERATION",
            source = SUFFIX,
            tags = listOf("energy", "regen"),
            effects = listOf(effect(STOCK_ENERGY_REGEN, INCREASED, best = 30.0..35.0, worst = 8.0..12.0)),
            tierCount = 5, bestItemLevel = 78
        ),
        ModifierTemplate(
            code = "INCREASED_ITEM_QUANTITY",
            source = SUFFIX,
            tags = listOf("quantity"),
            effects = listOf(effect(STOCK_QUANTITY, INCREASED, best = 8.0..10.0, worst = 2.0..3.0)),
            tierCount = 4, bestItemLevel = 80, weight = 400
        ),
        ModifierTemplate(
            code = "ADD_SPELL_LEECH",
            source = SUFFIX,
            tags = listOf("mana", "leech_spell"),
            effects = listOf(effect(STOCK_LEECH_MAGICAL, ADD, best = 0.8..1.0, worst = 0.2..0.3)),
            tierCount = 4, bestItemLevel = 79
        ),
        ModifierTemplate(
            code = "ADD_CONSTITUTION",
            source = SUFFIX,
            tags = listOf("attribute", "constitution"),
            effects = listOf(effect(STOCK_CONSTITUTION, ADD, best = 20.0..25.0, worst = 3.0..5.0)),
            tierCount = 6, bestItemLevel = 80
        ),

        // ---------- INFLUENCE: модификаторы, которые открывает влияние ----------
        // В пул шаблона не входят: их добавляет предмету его собственное влияние,
        // см. ModifierRoller.influencePool. Группы у них свои, как в POE.
        ModifierTemplate(
            code = "SHAPER_INCREASED_MAXIMUM_LIFE",
            source = PREFIX,
            tags = listOf("life", "influence"),
            effects = listOf(effect(STOCK_HEALTH, INCREASED, best = 8.0..10.0, worst = 4.0..5.0)),
            tierCount = 3, bestItemLevel = 75, influence = SHAPER
        ),
        ModifierTemplate(
            code = "SHAPER_INCREASED_MAXIMUM_ENERGY_SHIELD",
            source = PREFIX,
            tags = listOf("energy_shield", "influence"),
            effects = listOf(effect(STOCK_ENERGY_SHIELD, INCREASED, best = 8.0..10.0, worst = 4.0..5.0)),
            tierCount = 3, bestItemLevel = 75, influence = SHAPER
        ),
        ModifierTemplate(
            code = "SHAPER_INCREASED_SPELL_DAMAGE",
            source = PREFIX,
            tags = listOf("caster", "damage", "influence"),
            effects = listOf(effect(STOCK_ATTACK_MAGICAL, INCREASED, best = 20.0..25.0, worst = 10.0..14.0)),
            tierCount = 4, bestItemLevel = 78, influence = SHAPER
        ),
        ModifierTemplate(
            code = "SHAPER_INCREASED_AURA_EFFECT",
            source = SUFFIX,
            tags = listOf("aura", "influence"),
            effects = listOf(effect(STOCK_AURA_EFFECT, INCREASED, best = 8.0..10.0, worst = 4.0..5.0)),
            tierCount = 3, bestItemLevel = 80, weight = 500, influence = SHAPER
        ),
        ModifierTemplate(
            code = "SHAPER_ADD_SPELL_POWER",
            source = SUFFIX,
            tags = listOf("caster", "influence"),
            effects = listOf(effect(STOCK_CAST_STRENGTH, ADD, best = 15.0..20.0, worst = 5.0..8.0)),
            tierCount = 4, bestItemLevel = 78, influence = SHAPER
        ),
        ModifierTemplate(
            code = "ELDER_INCREASED_MAXIMUM_MANA",
            source = PREFIX,
            tags = listOf("mana", "influence"),
            effects = listOf(effect(STOCK_MANA, INCREASED, best = 10.0..12.0, worst = 5.0..7.0)),
            tierCount = 3, bestItemLevel = 75, influence = ELDER
        ),
        ModifierTemplate(
            code = "ELDER_INCREASED_CRITICAL_DAMAGE",
            source = PREFIX,
            tags = listOf("critical", "damage", "influence"),
            effects = listOf(effect(STOCK_CRITICAL_DAMAGE, INCREASED, best = 20.0..25.0, worst = 10.0..15.0)),
            tierCount = 4, bestItemLevel = 78, influence = ELDER
        ),
        ModifierTemplate(
            code = "ELDER_INCREASED_CURSE_EFFECT",
            source = SUFFIX,
            tags = listOf("curse", "influence"),
            effects = listOf(effect(STOCK_CURSE_EFFECT, INCREASED, best = 8.0..10.0, worst = 4.0..5.0)),
            tierCount = 3, bestItemLevel = 80, weight = 500, influence = ELDER
        ),
        ModifierTemplate(
            code = "ELDER_ADD_CRITICAL_STRIKE_MULTIPLIER",
            source = SUFFIX,
            tags = listOf("critical", "influence"),
            effects = listOf(effect(STOCK_CRITICAL_MULTIPLIER, ADD, best = 20.0..25.0, worst = 10.0..14.0)),
            tierCount = 4, bestItemLevel = 78, influence = ELDER
        ),
        ModifierTemplate(
            code = "ELDER_ADD_LEECH",
            source = SUFFIX,
            tags = listOf("leech", "influence"),
            effects = listOf(effect(STOCK_LEECH_ALL, ADD, best = 0.8..1.0, worst = 0.3..0.5)),
            tierCount = 4, bestItemLevel = 78, influence = ELDER
        ),

        // ---------- CRAFTED: модификаторы верстака ----------
        // Их не роллит ни одна сфера - их ставит верстак за сферы, см. CraftingBench.
        // Группа у каждого та же, что у выпадающего двойника: одно свойство дважды не встаёт.
        ModifierTemplate(
            code = "CRAFTED_ADD_MAXIMUM_LIFE",
            source = PREFIX,
            tags = listOf("life", "crafted"),
            effects = listOf(effect(STOCK_HEALTH, ADD, best = 70.0..79.0, worst = 25.0..34.0)),
            tierCount = 3, bestItemLevel = 1, group = "ADD_MAXIMUM_LIFE", crafted = true
        ),
        ModifierTemplate(
            code = "CRAFTED_ADD_MAXIMUM_MANA",
            source = PREFIX,
            tags = listOf("mana", "crafted"),
            effects = listOf(effect(STOCK_MANA, ADD, best = 55.0..64.0, worst = 25.0..34.0)),
            tierCount = 3, bestItemLevel = 1, group = "ADD_MAXIMUM_MANA", crafted = true
        ),
        ModifierTemplate(
            code = "CRAFTED_LOCAL_INCREASED_ARMOUR",
            source = PREFIX, isLocal = true,
            tags = listOf("armour", "defences", "crafted"),
            effects = listOf(effect(STOCK_ARMOR, INCREASED, best = 60.0..69.0, worst = 20.0..29.0)),
            tierCount = 3, bestItemLevel = 1, group = "LOCAL_INCREASED_ARMOUR", crafted = true
        ),
        ModifierTemplate(
            code = "CRAFTED_ADD_FIRE_RESISTANCE",
            source = SUFFIX,
            tags = listOf("resistance", "fire", "crafted"),
            effects = listOf(effect(STOCK_RESIST_FIRE, ADD, best = 29.0..35.0, worst = 16.0..20.0)),
            tierCount = 3, bestItemLevel = 1, group = "ADD_FIRE_RESISTANCE", crafted = true
        ),
        ModifierTemplate(
            code = "CRAFTED_ADD_COLD_RESISTANCE",
            source = SUFFIX,
            tags = listOf("resistance", "cold", "crafted"),
            effects = listOf(effect(STOCK_RESIST_COLD, ADD, best = 29.0..35.0, worst = 16.0..20.0)),
            tierCount = 3, bestItemLevel = 1, group = "ADD_COLD_RESISTANCE", crafted = true
        ),
        ModifierTemplate(
            code = "CRAFTED_ADD_LIGHTNING_RESISTANCE",
            source = SUFFIX,
            tags = listOf("resistance", "lightning", "crafted"),
            effects = listOf(effect(STOCK_RESIST_LIGHTNING, ADD, best = 29.0..35.0, worst = 16.0..20.0)),
            tierCount = 3, bestItemLevel = 1, group = "ADD_LIGHTNING_RESISTANCE", crafted = true
        ),
        ModifierTemplate(
            code = "CRAFTED_ADD_CHAOS_RESISTANCE",
            source = SUFFIX,
            tags = listOf("resistance", "chaos", "crafted"),
            effects = listOf(effect(STOCK_RESIST_CHAOS, ADD, best = 16.0..20.0, worst = 6.0..10.0)),
            tierCount = 3, bestItemLevel = 1, group = "ADD_CHAOS_RESISTANCE", crafted = true
        ),
        ModifierTemplate(
            code = "CRAFTED_ADD_STRENGTH",
            source = SUFFIX,
            tags = listOf("attribute", "strength", "crafted"),
            effects = listOf(effect(STOCK_STRENGTH, ADD, best = 31.0..35.0, worst = 15.0..20.0)),
            tierCount = 3, bestItemLevel = 1, group = "ADD_STRENGTH", crafted = true
        ),
        ModifierTemplate(
            code = "CRAFTED_ADD_DEXTERITY",
            source = SUFFIX,
            tags = listOf("attribute", "dexterity", "crafted"),
            effects = listOf(effect(STOCK_AGILITY, ADD, best = 31.0..35.0, worst = 15.0..20.0)),
            tierCount = 3, bestItemLevel = 1, group = "ADD_DEXTERITY", crafted = true
        ),
        ModifierTemplate(
            code = "CRAFTED_ADD_INTELLIGENCE",
            source = SUFFIX,
            tags = listOf("attribute", "intelligence", "crafted"),
            effects = listOf(effect(STOCK_INTELLECT, ADD, best = 31.0..35.0, worst = 15.0..20.0)),
            tierCount = 3, bestItemLevel = 1, group = "ADD_INTELLIGENCE", crafted = true
        ),

        // ---------- IMPLICIT: встроенные модификаторы базы ----------
        ModifierTemplate(
            code = "IMPLICIT_ADD_MAXIMUM_LIFE",
            source = IMPLICIT,
            tags = listOf("life", "implicit"),
            effects = listOf(effect(STOCK_HEALTH, ADD, best = 40.0..45.0, worst = 10.0..15.0)),
            tierCount = 3, bestItemLevel = 60
        ),
        ModifierTemplate(
            code = "IMPLICIT_ADD_ARMOUR",
            source = IMPLICIT,
            tags = listOf("armour", "defences", "implicit"),
            effects = listOf(effect(STOCK_ARMOR, ADD, best = 100.0..120.0, worst = 20.0..30.0)),
            tierCount = 3, bestItemLevel = 60
        ),
        ModifierTemplate(
            code = "IMPLICIT_ADD_ALL_ELEMENTAL_RESISTANCES",
            source = IMPLICIT,
            tags = listOf("resistance", "elemental", "implicit"),
            effects = listOf(effect(STOCK_RESIST_ALL, ADD, best = 12.0..16.0, worst = 4.0..6.0)),
            tierCount = 3, bestItemLevel = 60
        ),
        ModifierTemplate(
            code = "IMPLICIT_ADD_ALL_ATTRIBUTES",
            source = IMPLICIT,
            tags = listOf("attribute", "implicit", "hybrid"),
            effects = listOf(
                effect(STOCK_STRENGTH, ADD, best = 12.0..16.0, worst = 4.0..6.0),
                effect(STOCK_AGILITY, ADD, best = 12.0..16.0, worst = 4.0..6.0),
                effect(STOCK_INTELLECT, ADD, best = 12.0..16.0, worst = 4.0..6.0),
            ),
            tierCount = 3, bestItemLevel = 60
        ),

        // ---------- IMPLICIT 0.24.0: врождённые модификаторы баз PoE ----------
        // Роллятся на каждой копии по её item level; Blessed Orb перекатывает их значения.
        ModifierTemplate(
            code = "IMPLICIT_ADD_PHYSICAL_DAMAGE",
            source = IMPLICIT,
            tags = listOf("physical", "attack", "implicit"),
            effects = listOf(effect(STOCK_ATTACK_PHYSICAL, ADD, best = 4.0..6.0, worst = 1.0..2.0)),
            tierCount = 3, bestItemLevel = 60
        ),
        ModifierTemplate(
            code = "IMPLICIT_ADD_FIRE_DAMAGE",
            source = IMPLICIT,
            tags = listOf("fire", "attack", "implicit"),
            effects = listOf(effect(STOCK_ATTACK_FIRE, ADD, best = 8.0..12.0, worst = 2.0..4.0)),
            tierCount = 3, bestItemLevel = 60
        ),
        ModifierTemplate(
            code = "IMPLICIT_ADD_MAXIMUM_MANA",
            source = IMPLICIT,
            tags = listOf("mana", "implicit"),
            effects = listOf(effect(STOCK_MANA, ADD, best = 30.0..35.0, worst = 10.0..15.0)),
            tierCount = 3, bestItemLevel = 60
        ),
        ModifierTemplate(
            code = "IMPLICIT_ADD_FIRE_RESISTANCE",
            source = IMPLICIT,
            tags = listOf("resistance", "fire", "implicit"),
            effects = listOf(effect(STOCK_RESIST_FIRE, ADD, best = 25.0..30.0, worst = 10.0..15.0)),
            tierCount = 3, bestItemLevel = 60
        ),
        ModifierTemplate(
            code = "IMPLICIT_ADD_COLD_RESISTANCE",
            source = IMPLICIT,
            tags = listOf("resistance", "cold", "implicit"),
            effects = listOf(effect(STOCK_RESIST_COLD, ADD, best = 25.0..30.0, worst = 10.0..15.0)),
            tierCount = 3, bestItemLevel = 60
        ),
        ModifierTemplate(
            code = "IMPLICIT_ADD_LIGHTNING_RESISTANCE",
            source = IMPLICIT,
            tags = listOf("resistance", "lightning", "implicit"),
            effects = listOf(effect(STOCK_RESIST_LIGHTNING, ADD, best = 25.0..30.0, worst = 10.0..15.0)),
            tierCount = 3, bestItemLevel = 60
        ),
        ModifierTemplate(
            code = "IMPLICIT_ADD_CHAOS_RESISTANCE",
            source = IMPLICIT,
            tags = listOf("resistance", "chaos", "implicit"),
            effects = listOf(effect(STOCK_RESIST_CHAOS, ADD, best = 17.0..23.0, worst = 7.0..10.0)),
            tierCount = 3, bestItemLevel = 60
        ),
        ModifierTemplate(
            code = "IMPLICIT_ADD_ENERGY_SHIELD",
            source = IMPLICIT,
            tags = listOf("energy_shield", "implicit"),
            effects = listOf(effect(STOCK_ENERGY_SHIELD, ADD, best = 15.0..20.0, worst = 6.0..9.0)),
            tierCount = 3, bestItemLevel = 60
        ),
        ModifierTemplate(
            code = "IMPLICIT_INCREASED_CRITICAL_CHANCE",
            source = IMPLICIT,
            tags = listOf("critical", "implicit"),
            effects = listOf(effect(STOCK_CRITICAL_CHANCE, INCREASED, best = 20.0..30.0, worst = 10.0..15.0)),
            tierCount = 3, bestItemLevel = 60
        ),
        ModifierTemplate(
            code = "IMPLICIT_ADD_CRITICAL_MULTIPLIER",
            source = IMPLICIT,
            tags = listOf("critical", "implicit"),
            effects = listOf(effect(STOCK_CRITICAL_MULTIPLIER, ADD, best = 20.0..25.0, worst = 10.0..15.0)),
            tierCount = 3, bestItemLevel = 60
        ),
        ModifierTemplate(
            code = "IMPLICIT_ADD_LIFE_REGENERATION",
            source = IMPLICIT,
            tags = listOf("life", "regen", "implicit"),
            effects = listOf(effect(STOCK_HEALTH_REGEN, ADD, best = 3.0..4.0, worst = 1.0..2.0)),
            tierCount = 3, bestItemLevel = 60
        ),
        ModifierTemplate(
            code = "IMPLICIT_ADD_STRENGTH",
            source = IMPLICIT,
            tags = listOf("attribute", "strength", "implicit"),
            effects = listOf(effect(STOCK_STRENGTH, ADD, best = 25.0..30.0, worst = 10.0..15.0)),
            tierCount = 3, bestItemLevel = 60
        ),
        ModifierTemplate(
            code = "IMPLICIT_ADD_DEXTERITY",
            source = IMPLICIT,
            tags = listOf("attribute", "dexterity", "implicit"),
            effects = listOf(effect(STOCK_AGILITY, ADD, best = 25.0..30.0, worst = 10.0..15.0)),
            tierCount = 3, bestItemLevel = 60
        ),
        ModifierTemplate(
            code = "IMPLICIT_ADD_INTELLIGENCE",
            source = IMPLICIT,
            tags = listOf("attribute", "intelligence", "implicit"),
            effects = listOf(effect(STOCK_INTELLECT, ADD, best = 25.0..30.0, worst = 10.0..15.0)),
            tierCount = 3, bestItemLevel = 60
        ),
        ModifierTemplate(
            code = "IMPLICIT_INCREASED_ITEM_RARITY",
            source = IMPLICIT,
            tags = listOf("rarity", "implicit"),
            effects = listOf(effect(STOCK_RARITY, INCREASED, best = 20.0..30.0, worst = 12.0..16.0)),
            tierCount = 3, bestItemLevel = 60
        ),
        ModifierTemplate(
            code = "IMPLICIT_INCREASED_PHYSICAL_DAMAGE",
            source = IMPLICIT,
            tags = listOf("physical", "damage", "implicit"),
            effects = listOf(effect(STOCK_ATTACK_PHYSICAL, INCREASED, best = 18.0..24.0, worst = 8.0..12.0)),
            tierCount = 3, bestItemLevel = 60
        ),
        ModifierTemplate(
            code = "IMPLICIT_ADD_STUN_THRESHOLD",
            source = IMPLICIT,
            tags = listOf("stun", "implicit"),
            effects = listOf(effect(STOCK_STUN_THRESHOLD, ADD, best = 60.0..80.0, worst = 20.0..30.0)),
            tierCount = 3, bestItemLevel = 60
        ),
        ModifierTemplate(
            code = "IMPLICIT_ADD_LIFE_LEECH",
            source = IMPLICIT,
            tags = listOf("leech", "physical", "implicit"),
            effects = listOf(effect(STOCK_LEECH_PHYSICAL, ADD, best = 0.6..0.8, worst = 0.2..0.4)),
            tierCount = 3, bestItemLevel = 60
        ),
        ModifierTemplate(
            code = "IMPLICIT_INCREASED_SPELL_DAMAGE",
            source = IMPLICIT,
            tags = listOf("caster", "damage", "implicit"),
            effects = listOf(effect(STOCK_ATTACK_MAGICAL, INCREASED, best = 15.0..20.0, worst = 8.0..10.0)),
            tierCount = 3, bestItemLevel = 60
        ),
        ModifierTemplate(
            code = "IMPLICIT_INCREASED_MOVEMENT_SPEED",
            source = IMPLICIT,
            tags = listOf("speed", "implicit"),
            effects = listOf(effect(STOCK_MOVEMENT_SPEED, INCREASED, best = 8.0..10.0, worst = 3.0..5.0)),
            tierCount = 3, bestItemLevel = 60
        ),
        ModifierTemplate(
            code = "IMPLICIT_BLOCK_CHANCE_BASE",
            source = IMPLICIT, isLocal = true,
            tags = listOf("block", "defences", "implicit", "base"),
            effects = listOf(effect(STOCK_BLOCK_CHANCE, ADD, best = 1.0..1.0)),
            tierCount = 1, bestItemLevel = 1
        ),

        // ---------- LOCAL: стихийный урон оружия ----------
        // Складывается внутри оружия, как его физический урон, и наружу уходит уже суммой.
        ModifierTemplate(
            code = "LOCAL_ADD_FIRE_DAMAGE",
            source = PREFIX, isLocal = true,
            tags = listOf("fire", "elemental", "damage", "attack", "weapon"),
            effects = listOf(effect(STOCK_ATTACK_FIRE, ADD, best = 45.0..80.0, worst = 2.0..4.0)),
            tierCount = 8, bestItemLevel = 82
        ),
        ModifierTemplate(
            code = "LOCAL_ADD_COLD_DAMAGE",
            source = PREFIX, isLocal = true,
            tags = listOf("cold", "elemental", "damage", "attack", "weapon"),
            effects = listOf(effect(STOCK_ATTACK_COLD, ADD, best = 45.0..80.0, worst = 2.0..4.0)),
            tierCount = 8, bestItemLevel = 82
        ),
        ModifierTemplate(
            code = "LOCAL_ADD_LIGHTNING_DAMAGE",
            source = PREFIX, isLocal = true,
            tags = listOf("lightning", "elemental", "damage", "attack", "weapon"),
            effects = listOf(effect(STOCK_ATTACK_LIGHTNING, ADD, best = 45.0..80.0, worst = 2.0..4.0)),
            tierCount = 8, bestItemLevel = 82
        ),

        // ---------- ENCHANTMENT: лабиринтные энчанты ----------
        ModifierTemplate(
            code = "ENCHANT_INCREASED_MOVEMENT_SPEED",
            source = ENCHANTMENT,
            tags = listOf("speed", "enchantment"),
            effects = listOf(effect(STOCK_MOVEMENT_SPEED, INCREASED, best = 16.0..16.0, worst = 8.0..8.0)),
            tierCount = 3, bestItemLevel = 68
        ),
        ModifierTemplate(
            code = "ENCHANT_INCREASED_AURA_EFFECT",
            source = ENCHANTMENT,
            tags = listOf("aura", "enchantment"),
            effects = listOf(effect(STOCK_AURA_EFFECT, INCREASED, best = 12.0..12.0, worst = 4.0..4.0)),
            tierCount = 3, bestItemLevel = 68
        ),

        // ---------- CORRUPTION: vaal-модификаторы ----------
        ModifierTemplate(
            code = "CORRUPTED_ADD_ALL_ELEMENTAL_RESISTANCES",
            source = CORRUPTION,
            tags = listOf("resistance", "elemental", "corrupted"),
            effects = listOf(effect(STOCK_RESIST_ALL, ADD, best = 10.0..12.0, worst = 5.0..7.0)),
            tierCount = 2, bestItemLevel = 68
        ),
        ModifierTemplate(
            code = "CORRUPTED_MORE_PHYSICAL_DAMAGE",
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
            source = IMPLICIT, isLocal = true,
            tags = listOf("armour", "defences", "implicit", "base"),
            effects = listOf(effect(STOCK_ARMOR, ADD, best = 1.0..1.0)),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "IMPLICIT_EVASION_BASE",
            source = IMPLICIT, isLocal = true,
            tags = listOf("evasion", "defences", "implicit", "base"),
            effects = listOf(effect(STOCK_EVASION, ADD, best = 1.0..1.0)),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "IMPLICIT_ENERGY_SHIELD_BASE",
            source = IMPLICIT, isLocal = true,
            tags = listOf("energy_shield", "defences", "implicit", "base"),
            effects = listOf(effect(STOCK_ENERGY_SHIELD, ADD, best = 1.0..1.0)),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "IMPLICIT_PHYSICAL_DAMAGE_BASE",
            source = IMPLICIT, isLocal = true,
            tags = listOf("physical", "damage", "attack", "implicit", "base"),
            effects = listOf(effect(STOCK_ATTACK_PHYSICAL, ADD, best = 1.0..1.0)),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "IMPLICIT_ATTACK_SPEED_BASE",
            source = IMPLICIT, isLocal = true,
            tags = listOf("speed", "attack", "implicit", "base"),
            effects = listOf(effect(STOCK_ATTACK_SPEED, ADD, best = 1.0..1.0)),
            tierCount = 1, bestItemLevel = 1
        ),

        ModifierTemplate(
            code = "LOCAL_ADD_ARMOUR",
            source = PREFIX, isLocal = true,
            tags = listOf("armour", "defences"),
            effects = listOf(effect(STOCK_ARMOR, ADD, best = 380.0..440.0, worst = 8.0..15.0)),
            tierCount = 8, bestItemLevel = 84
        ),
        ModifierTemplate(
            code = "LOCAL_INCREASED_ARMOUR",
            source = PREFIX, isLocal = true,
            tags = listOf("armour", "defences"),
            effects = listOf(effect(STOCK_ARMOR, INCREASED, best = 100.0..109.0, worst = 6.0..13.0)),
            tierCount = 8, bestItemLevel = 84
        ),
        ModifierTemplate(
            code = "LOCAL_ADD_EVASION_RATING",
            source = PREFIX, isLocal = true,
            tags = listOf("evasion", "defences"),
            effects = listOf(effect(STOCK_EVASION, ADD, best = 380.0..440.0, worst = 8.0..15.0)),
            tierCount = 8, bestItemLevel = 84
        ),
        ModifierTemplate(
            code = "LOCAL_INCREASED_EVASION_RATING",
            source = PREFIX, isLocal = true,
            tags = listOf("evasion", "defences"),
            effects = listOf(effect(STOCK_EVASION, INCREASED, best = 100.0..109.0, worst = 6.0..13.0)),
            tierCount = 8, bestItemLevel = 84
        ),
        ModifierTemplate(
            code = "LOCAL_ADD_ENERGY_SHIELD",
            source = PREFIX, isLocal = true,
            tags = listOf("energy_shield", "defences"),
            effects = listOf(effect(STOCK_ENERGY_SHIELD, ADD, best = 65.0..71.0, worst = 3.0..5.0)),
            tierCount = 8, bestItemLevel = 86
        ),
        ModifierTemplate(
            code = "LOCAL_INCREASED_ENERGY_SHIELD",
            source = PREFIX, isLocal = true,
            tags = listOf("energy_shield", "defences"),
            effects = listOf(effect(STOCK_ENERGY_SHIELD, INCREASED, best = 100.0..109.0, worst = 6.0..13.0)),
            tierCount = 8, bestItemLevel = 84
        ),
        ModifierTemplate(
            code = "LOCAL_INCREASED_ARMOUR_AND_EVASION",
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
            source = PREFIX, isLocal = true,
            tags = listOf("physical", "damage", "attack"),
            effects = listOf(effect(STOCK_ATTACK_PHYSICAL, ADD, best = 25.0..45.0, worst = 1.0..3.0)),
            tierCount = 8, bestItemLevel = 77
        ),
        ModifierTemplate(
            code = "LOCAL_INCREASED_PHYSICAL_DAMAGE",
            source = PREFIX, isLocal = true,
            tags = listOf("physical", "damage", "attack"),
            effects = listOf(effect(STOCK_ATTACK_PHYSICAL, INCREASED, best = 170.0..179.0, worst = 40.0..49.0)),
            tierCount = 8, bestItemLevel = 81
        ),
        ModifierTemplate(
            code = "LOCAL_INCREASED_ATTACK_SPEED",
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
            source = PASSIVE,
            tags = listOf("life", "attribute", "conversion"),
            effects = listOf(
                conversion(STOCK_HEALTH, ADD, perStat = STOCK_STRENGTH, perAmount = 2.0)
            ),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "CONVERT_DEXTERITY_TO_EVASION",
            source = PASSIVE,
            tags = listOf("evasion", "attribute", "conversion"),
            effects = listOf(
                conversion(STOCK_EVASION, ADD, perStat = STOCK_AGILITY, perAmount = 1.0)
            ),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "CONVERT_INTELLIGENCE_TO_MANA",
            source = PASSIVE,
            tags = listOf("mana", "attribute", "conversion"),
            effects = listOf(
                conversion(STOCK_MANA, ADD, perStat = STOCK_INTELLECT, perAmount = 2.0)
            ),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "CONVERT_INTELLIGENCE_TO_ENERGY_SHIELD",
            source = PASSIVE,
            tags = listOf("energy_shield", "attribute", "conversion"),
            effects = listOf(
                conversion(STOCK_ENERGY_SHIELD, INCREASED, perStat = STOCK_INTELLECT, perAmount = 5.0)
            ),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "CONVERT_STRENGTH_TO_PHYSICAL_DAMAGE",
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
            source = PASSIVE,
            tags = listOf("critical", "passive"),
            effects = listOf(effect(STOCK_CRITICAL_CHANCE, SET, best = 0.0..0.0)),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "PASSIVE_SET_MAXIMUM_LIFE",
            source = PASSIVE,
            tags = listOf("life", "passive"),
            effects = listOf(effect(STOCK_HEALTH, SET, best = 1.0..1.0)),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "PASSIVE_SET_MAXIMUM_MANA",
            source = PASSIVE,
            tags = listOf("mana", "passive"),
            effects = listOf(effect(STOCK_MANA, SET, best = 0.0..0.0)),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "PASSIVE_SET_EVASION_RATING",
            source = PASSIVE,
            tags = listOf("evasion", "defences", "passive"),
            effects = listOf(effect(STOCK_EVASION, SET, best = 0.0..0.0)),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "PASSIVE_SET_CHAOS_RESISTANCE",
            source = PASSIVE,
            tags = listOf("resistance", "chaos", "passive"),
            effects = listOf(effect(STOCK_RESIST_CHAOS, SET, best = 100.0..100.0)),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "PASSIVE_MORE_ARMOUR",
            source = PASSIVE,
            tags = listOf("armour", "defences", "passive"),
            effects = listOf(effect(STOCK_ARMOR, MORE, best = 100.0..100.0)),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "PASSIVE_MORE_EVASION_RATING",
            source = PASSIVE,
            tags = listOf("evasion", "defences", "passive"),
            effects = listOf(effect(STOCK_EVASION, MORE, best = 30.0..30.0)),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "PASSIVE_MORE_MAXIMUM_LIFE",
            source = PASSIVE,
            tags = listOf("life", "passive"),
            effects = listOf(effect(STOCK_HEALTH, MORE, best = 20.0..20.0)),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "PASSIVE_MORE_PHYSICAL_DAMAGE",
            source = PASSIVE,
            tags = listOf("physical", "damage", "passive"),
            effects = listOf(effect(STOCK_ATTACK_PHYSICAL, MORE, best = 20.0..20.0)),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "PASSIVE_MORE_STUN_THRESHOLD",
            source = PASSIVE,
            tags = listOf("defences", "passive"),
            effects = listOf(effect(STOCK_STUN_THRESHOLD, MORE, best = 100.0..100.0)),
            tierCount = 1, bestItemLevel = 1
        ),

        // ---------- PASSIVE: проценты, которые бывают только на дереве ----------
        // На предметах таких модификаторов в POE нет, поэтому источник PASSIVE:
        // в пулы роллов они не попадают.
        ModifierTemplate(
            code = "INCREASED_MAXIMUM_LIFE",
            source = PASSIVE,
            tags = listOf("life", "passive"),
            effects = listOf(effect(STOCK_HEALTH, INCREASED, best = 10.0..10.0)),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "INCREASED_MAXIMUM_MANA",
            source = PASSIVE,
            tags = listOf("mana", "passive"),
            effects = listOf(effect(STOCK_MANA, INCREASED, best = 16.0..16.0)),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "INCREASED_STUN_THRESHOLD",
            source = PASSIVE,
            tags = listOf("defences", "passive"),
            effects = listOf(effect(STOCK_STUN_THRESHOLD, INCREASED, best = 20.0..20.0)),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "INCREASED_ATTACK_AND_CAST_SPEED",
            source = PASSIVE,
            tags = listOf("speed", "attack", "caster", "hybrid", "passive"),
            effects = listOf(
                effect(STOCK_ATTACK_SPEED, INCREASED, best = 5.0..5.0),
                effect(STOCK_CAST_SPEED, INCREASED, best = 5.0..5.0),
            ),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "INCREASED_ELEMENTAL_DAMAGE",
            source = PASSIVE,
            tags = listOf("elemental", "fire", "cold", "lightning", "damage", "hybrid", "passive"),
            effects = listOf(
                effect(STOCK_ATTACK_FIRE, INCREASED, best = 30.0..30.0),
                effect(STOCK_ATTACK_COLD, INCREASED, best = 30.0..30.0),
                effect(STOCK_ATTACK_LIGHTNING, INCREASED, best = 30.0..30.0),
            ),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "PASSIVE_SET_LIFE_REGENERATION",
            source = PASSIVE,
            tags = listOf("life", "regen", "passive"),
            effects = listOf(effect(STOCK_HEALTH_REGEN, SET, best = 0.0..0.0)),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "PASSIVE_MORE_LIFE_LEECH",
            source = PASSIVE,
            tags = listOf("leech", "life", "passive"),
            effects = listOf(effect(STOCK_LEECH_PHYSICAL, MORE, best = 100.0..100.0)),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "PASSIVE_SET_CRITICAL_DAMAGE",
            source = PASSIVE,
            tags = listOf("critical", "damage", "passive"),
            effects = listOf(effect(STOCK_CRITICAL_DAMAGE, SET, best = 0.0..0.0)),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "PASSIVE_MORE_ELEMENTAL_DAMAGE",
            source = PASSIVE,
            tags = listOf("elemental", "damage", "hybrid", "passive"),
            effects = listOf(
                effect(STOCK_ATTACK_FIRE, MORE, best = 40.0..40.0),
                effect(STOCK_ATTACK_COLD, MORE, best = 40.0..40.0),
                effect(STOCK_ATTACK_LIGHTNING, MORE, best = 40.0..40.0),
            ),
            tierCount = 1, bestItemLevel = 1
        ),

        ModifierTemplate(
            code = "CORRUPTED_SET_CRITICAL_STRIKE_CHANCE",
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

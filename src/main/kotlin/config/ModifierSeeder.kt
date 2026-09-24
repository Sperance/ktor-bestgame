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
import application.enums.EnumStatStock.STOCK_LIGHT_RADIUS
import application.enums.EnumStatStock.STOCK_CHEST_QUANTITY
import application.enums.EnumStatStock.STOCK_SPELL_BLOCK
import application.enums.EnumStatStock.STOCK_PHYSICAL_REDUCTION
import application.enums.EnumStatStock.STOCK_AVOID_STUN
import application.enums.EnumStatStock.STOCK_RESIST_MAX_FIRE
import application.enums.EnumStatStock.STOCK_RESIST_MAX_COLD
import application.enums.EnumStatStock.STOCK_RESIST_MAX_LIGHTNING
import application.enums.EnumStatStock.STOCK_RESIST_MAX_CHAOS
import application.enums.EnumStatStock.STOCK_RESIST_MAX_ALL
import application.enums.EnumStatStock.STOCK_HEALTH_ON_KILL
import application.enums.EnumStatStock.STOCK_MANA_ON_KILL
import application.enums.EnumStatStock.STOCK_HEALTH_ON_HIT
import application.enums.EnumStatStock.STOCK_MANA_ON_HIT
import application.enums.EnumStatStock.STOCK_FLASK_CHARGES
import application.enums.EnumStatStock.STOCK_FLASK_RECOVERY
import application.enums.EnumStatStock.STOCK_IGNITE_CHANCE
import application.enums.EnumStatStock.STOCK_FREEZE_CHANCE
import application.enums.EnumStatStock.STOCK_SHOCK_CHANCE
import application.enums.EnumStatStock.STOCK_POISON_CHANCE
import application.enums.EnumStatStock.STOCK_BLEED_CHANCE
import application.enums.EnumStatStock.STOCK_BURNING_DAMAGE
import application.enums.EnumStatStock.STOCK_POISON_DAMAGE
import application.enums.EnumStatStock.STOCK_BLEED_DAMAGE
import application.enums.EnumStatStock.STOCK_AVOID_IGNITE
import application.enums.EnumStatStock.STOCK_AVOID_CHILL
import application.enums.EnumStatStock.STOCK_AVOID_FREEZE
import application.enums.EnumStatStock.STOCK_AVOID_SHOCK
import application.enums.EnumStatStock.STOCK_AVOID_POISON
import application.enums.EnumStatStock.STOCK_AVOID_BLEED
import application.enums.EnumStatStock.STOCK_IGNITE_DURATION_ON_SELF
import application.enums.EnumStatStock.STOCK_CHILL_DURATION_ON_SELF
import application.enums.EnumStatStock.STOCK_FREEZE_DURATION_ON_SELF
import application.enums.EnumStatStock.STOCK_SHOCK_DURATION_ON_SELF
import application.enums.EnumStatStock.STOCK_POISON_DURATION_ON_SELF
import application.enums.EnumStatStock.STOCK_BLEED_DURATION_ON_SELF
import application.enums.EnumStatStock.MAP_MONSTER_LIFE
import application.enums.EnumStatStock.MAP_MONSTER_DAMAGE
import application.enums.EnumStatStock.MAP_MONSTER_SPEED
import application.enums.EnumStatStock.MAP_MONSTER_RESIST
import application.enums.EnumStatStock.MAP_PACK_SIZE
import application.enums.EnumStatStock.MAP_MONSTER_RARITY
import application.enums.EnumStatStock.MAP_HERO_LIGHT
import application.enums.EnumStatStock.MAP_HERO_FLASK
import application.enums.EnumStatStock.MAP_HERO_RESIST
import application.enums.EnumStatStock.MAP_HERO_REGEN
import application.enums.EnumStatStock.MAP_CHESTS
import application.enums.EnumStatStock.MAP_QUANTITY
import application.enums.EnumStatStock.MAP_RARITY
import application.enums.EnumStatStock.MAP_EXPERIENCE
import application.enums.EnumStatStock.STOCK_WORK_SPEED
import application.enums.EnumStatStock.STOCK_WORK_YIELD
import application.enums.EnumStatStock.STOCK_WORK_LUCK
import application.enums.EnumStatStock.STOCK_WORK_EXPERIENCE
import application.enums.EnumStatStock.STOCK_WORK_FIND
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
            code = "INCREASED_LIGHT_RADIUS",
            source = SUFFIX,
            tags = listOf("light"),
            effects = listOf(effect(STOCK_LIGHT_RADIUS, INCREASED, best = 20.0..25.0, worst = 5.0..8.0)),
            tierCount = 3, bestItemLevel = 60, weight = 500
        ),
        // ---------- Карты (0.35.0): префиксы - монстры и содержимое, суффиксы - герой и награды ----------
        ModifierTemplate(
            code = "MAP_MONSTER_LIFE",
            source = PREFIX,
            tags = listOf("map"),
            effects = listOf(effect(MAP_MONSTER_LIFE, INCREASED, best = 25.0..30.0, worst = 10.0..15.0)),
            tierCount = 3, bestItemLevel = 15
        ),
        ModifierTemplate(
            code = "MAP_MONSTER_DAMAGE",
            source = PREFIX,
            tags = listOf("map"),
            effects = listOf(effect(MAP_MONSTER_DAMAGE, INCREASED, best = 20.0..25.0, worst = 8.0..12.0)),
            tierCount = 3, bestItemLevel = 15
        ),
        ModifierTemplate(
            code = "MAP_MONSTER_SPEED",
            source = PREFIX,
            tags = listOf("map"),
            effects = listOf(effect(MAP_MONSTER_SPEED, INCREASED, best = 15.0..20.0, worst = 5.0..8.0)),
            tierCount = 3, bestItemLevel = 15
        ),
        ModifierTemplate(
            code = "MAP_MONSTER_RESIST",
            source = PREFIX,
            tags = listOf("map"),
            effects = listOf(effect(MAP_MONSTER_RESIST, ADD, best = 25.0..30.0, worst = 10.0..15.0)),
            tierCount = 3, bestItemLevel = 15
        ),
        ModifierTemplate(
            code = "MAP_PACK_SIZE",
            source = PREFIX,
            tags = listOf("map"),
            effects = listOf(effect(MAP_PACK_SIZE, INCREASED, best = 25.0..30.0, worst = 8.0..12.0)),
            tierCount = 3, bestItemLevel = 15
        ),
        ModifierTemplate(
            code = "MAP_MONSTER_RARITY",
            source = PREFIX,
            tags = listOf("map"),
            effects = listOf(effect(MAP_MONSTER_RARITY, INCREASED, best = 40.0..50.0, worst = 15.0..20.0)),
            tierCount = 3, bestItemLevel = 15
        ),
        ModifierTemplate(
            code = "MAP_HERO_LIGHT",
            source = SUFFIX,
            tags = listOf("map"),
            effects = listOf(effect(MAP_HERO_LIGHT, INCREASED, best = 30.0..35.0, worst = 10.0..15.0)),
            tierCount = 3, bestItemLevel = 15
        ),
        ModifierTemplate(
            code = "MAP_HERO_FLASK",
            source = SUFFIX,
            tags = listOf("map"),
            effects = listOf(effect(MAP_HERO_FLASK, ADD, best = 2.0..2.0, worst = 1.0..1.0)),
            tierCount = 2, bestItemLevel = 11
        ),
        ModifierTemplate(
            code = "MAP_HERO_RESIST",
            source = SUFFIX,
            tags = listOf("map"),
            effects = listOf(effect(MAP_HERO_RESIST, ADD, best = 15.0..20.0, worst = 6.0..10.0)),
            tierCount = 3, bestItemLevel = 15
        ),
        ModifierTemplate(
            code = "MAP_HERO_REGEN",
            source = SUFFIX,
            tags = listOf("map"),
            effects = listOf(effect(MAP_HERO_REGEN, INCREASED, best = 50.0..60.0, worst = 20.0..30.0)),
            tierCount = 3, bestItemLevel = 15
        ),
        ModifierTemplate(
            code = "MAP_CHESTS",
            source = SUFFIX,
            tags = listOf("map"),
            effects = listOf(effect(MAP_CHESTS, ADD, best = 1.0..1.0, worst = 1.0..1.0)),
            tierCount = 1, bestItemLevel = 1
        ),
        ModifierTemplate(
            code = "MAP_QUANTITY",
            source = SUFFIX,
            tags = listOf("map"),
            effects = listOf(effect(MAP_QUANTITY, INCREASED, best = 12.0..15.0, worst = 4.0..6.0)),
            tierCount = 3, bestItemLevel = 15
        ),
        ModifierTemplate(
            code = "MAP_RARITY",
            source = SUFFIX,
            tags = listOf("map"),
            effects = listOf(effect(MAP_RARITY, INCREASED, best = 25.0..30.0, worst = 8.0..12.0)),
            tierCount = 3, bestItemLevel = 15
        ),
        ModifierTemplate(
            code = "MAP_EXPERIENCE",
            source = SUFFIX,
            tags = listOf("map"),
            effects = listOf(effect(MAP_EXPERIENCE, INCREASED, best = 8.0..10.0, worst = 3.0..4.0)),
            tierCount = 3, bestItemLevel = 15
        ),
        // ---------- Ремёсла (0.37.0): аффиксы инструментов и эффекты ветки «Ремесло» ----------
        ModifierTemplate(
            code = "WORK_SPEED",
            source = PREFIX,
            tags = listOf("work"),
            effects = listOf(effect(STOCK_WORK_SPEED, INCREASED, best = 22.0..25.0, worst = 6.0..8.0)),
            tierCount = 4, bestItemLevel = 45
        ),
        ModifierTemplate(
            code = "WORK_YIELD",
            source = PREFIX,
            tags = listOf("work"),
            effects = listOf(effect(STOCK_WORK_YIELD, ADD, best = 26.0..30.0, worst = 8.0..10.0)),
            tierCount = 4, bestItemLevel = 45
        ),
        ModifierTemplate(
            code = "WORK_LUCK",
            source = SUFFIX,
            tags = listOf("work"),
            effects = listOf(effect(STOCK_WORK_LUCK, ADD, best = 26.0..30.0, worst = 8.0..10.0)),
            tierCount = 4, bestItemLevel = 45
        ),
        ModifierTemplate(
            code = "WORK_EXPERIENCE",
            source = SUFFIX,
            tags = listOf("work"),
            effects = listOf(effect(STOCK_WORK_EXPERIENCE, INCREASED, best = 18.0..20.0, worst = 5.0..6.0)),
            tierCount = 3, bestItemLevel = 40
        ),
        ModifierTemplate(
            code = "WORK_FIND",
            source = SUFFIX,
            tags = listOf("work"),
            effects = listOf(effect(STOCK_WORK_FIND, INCREASED, best = 35.0..40.0, worst = 10.0..12.0)),
            tierCount = 3, bestItemLevel = 40
        ),
        ModifierTemplate(
            code = "INCREASED_CHEST_QUANTITY",
            source = SUFFIX,
            tags = listOf("quantity"),
            effects = listOf(effect(STOCK_CHEST_QUANTITY, INCREASED, best = 28.0..30.0, worst = 10.0..12.0)),
            tierCount = 3, bestItemLevel = 70, weight = 300
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

        // ---------- 0.36.0: модификаторы POE по их таблицам тиров ----------
        // Тиры заданы строками, как в POE, а не краями: item level и диапазон у каждого свой.
        tabled(
            code = "INCREASED_FIRE_DAMAGE", source = SUFFIX,
            stats = listOf(STOCK_ATTACK_FIRE to INCREASED),
            tiers = listOf(
                tier(81, 23.0..26.0),
                tier(60, 18.0..22.0),
                tier(30, 13.0..17.0),
                tier(15, 8.0..12.0),
                tier(8, 3.0..7.0),
            ),
            tags = listOf("fire", "damage")
        ),
        tabled(
            code = "INCREASED_COLD_DAMAGE", source = SUFFIX,
            stats = listOf(STOCK_ATTACK_COLD to INCREASED),
            tiers = listOf(
                tier(81, 23.0..26.0),
                tier(60, 18.0..22.0),
                tier(30, 13.0..17.0),
                tier(15, 8.0..12.0),
                tier(8, 3.0..7.0),
            ),
            tags = listOf("cold", "damage")
        ),
        tabled(
            code = "INCREASED_LIGHTNING_DAMAGE", source = SUFFIX,
            stats = listOf(STOCK_ATTACK_LIGHTNING to INCREASED),
            tiers = listOf(
                tier(81, 23.0..26.0),
                tier(60, 18.0..22.0),
                tier(30, 13.0..17.0),
                tier(15, 8.0..12.0),
                tier(8, 3.0..7.0),
            ),
            tags = listOf("lightning", "damage")
        ),
        tabled(
            code = "INCREASED_CHAOS_DAMAGE", source = SUFFIX,
            stats = listOf(STOCK_ATTACK_CHAOS to INCREASED),
            tiers = listOf(
                tier(81, 23.0..26.0),
                tier(60, 18.0..22.0),
                tier(30, 13.0..17.0),
                tier(15, 8.0..12.0),
                tier(8, 3.0..7.0),
            ),
            tags = listOf("chaos", "damage")
        ),
        tabled(
            code = "INCREASED_ELEMENTAL_DAMAGE_WITH_ATTACKS", source = PREFIX,
            stats = listOf(STOCK_ATTACK_FIRE to INCREASED, STOCK_ATTACK_COLD to INCREASED, STOCK_ATTACK_LIGHTNING to INCREASED),
            tiers = listOf(
                tier(81, 37.0..42.0, 37.0..42.0, 37.0..42.0),
                tier(60, 31.0..36.0, 31.0..36.0, 31.0..36.0),
                tier(30, 21.0..30.0, 21.0..30.0, 21.0..30.0),
                tier(15, 11.0..20.0, 11.0..20.0, 11.0..20.0),
                tier(4, 5.0..10.0, 5.0..10.0, 5.0..10.0),
            ),
            tags = listOf("elemental", "damage", "attack")
        ),
        tabled(
            code = "GLOBAL_ADD_ENERGY_SHIELD", source = PREFIX,
            stats = listOf(STOCK_ENERGY_SHIELD to ADD),
            tiers = listOf(
                tier(74, 48.0..51.0),
                tier(64, 38.0..43.0),
                tier(56, 32.0..37.0),
                tier(46, 27.0..31.0),
                tier(38, 23.0..26.0),
                tier(30, 18.0..22.0),
                tier(24, 13.0..17.0),
                tier(16, 8.0..12.0),
                tier(3, 1.0..5.0),
            ),
            tags = listOf("energy_shield", "defences")
        ),
        tabled(
            code = "GLOBAL_INCREASED_ATTACK_SPEED", source = SUFFIX,
            stats = listOf(STOCK_ATTACK_SPEED to INCREASED),
            tiers = listOf(
                tier(76, 14.0..16.0),
                tier(45, 11.0..13.0),
                tier(15, 8.0..10.0),
                tier(1, 5.0..7.0),
            ),
            tags = listOf("attack", "speed")
        ),
        // Состояния: у героя база поджога, шока, яда и кровотечения - ноль, шанс дают только они
        tabled(
            code = "CHANCE_TO_IGNITE", source = SUFFIX,
            stats = listOf(STOCK_IGNITE_CHANCE to ADD),
            tiers = listOf(
                tier(76, 26.0..30.0),
                tier(45, 21.0..25.0),
                tier(15, 15.0..20.0),
            ),
            tags = listOf("fire", "ailment")
        ),
        tabled(
            code = "CHANCE_TO_FREEZE", source = SUFFIX,
            stats = listOf(STOCK_FREEZE_CHANCE to ADD),
            tiers = listOf(
                tier(76, 26.0..30.0),
                tier(45, 21.0..25.0),
                tier(15, 15.0..20.0),
            ),
            tags = listOf("cold", "ailment")
        ),
        tabled(
            code = "CHANCE_TO_SHOCK", source = SUFFIX,
            stats = listOf(STOCK_SHOCK_CHANCE to ADD),
            tiers = listOf(
                tier(76, 26.0..30.0),
                tier(45, 21.0..25.0),
                tier(15, 15.0..20.0),
            ),
            tags = listOf("lightning", "ailment")
        ),
        tabled(
            code = "CHANCE_TO_POISON", source = SUFFIX,
            stats = listOf(STOCK_POISON_CHANCE to ADD),
            tiers = listOf(
                tier(70, 31.0..40.0),
                tier(40, 21.0..30.0),
                tier(10, 10.0..20.0),
            ),
            tags = listOf("chaos", "ailment")
        ),
        tabled(
            code = "CHANCE_TO_BLEED", source = SUFFIX,
            stats = listOf(STOCK_BLEED_CHANCE to ADD),
            tiers = listOf(
                tier(60, 20.0..25.0),
                tier(36, 15.0..19.0),
                tier(12, 10.0..14.0),
            ),
            tags = listOf("physical", "ailment")
        ),
        tabled(
            code = "INCREASED_BURNING_DAMAGE", source = PREFIX,
            stats = listOf(STOCK_BURNING_DAMAGE to ADD),
            tiers = listOf(
                tier(70, 50.0..59.0),
                tier(50, 40.0..49.0),
                tier(30, 30.0..39.0),
                tier(12, 20.0..29.0),
            ),
            tags = listOf("fire", "ailment", "damage")
        ),
        tabled(
            code = "INCREASED_POISON_DAMAGE", source = PREFIX,
            stats = listOf(STOCK_POISON_DAMAGE to ADD),
            tiers = listOf(
                tier(70, 41.0..50.0),
                tier(50, 31.0..40.0),
                tier(30, 21.0..30.0),
                tier(12, 11.0..20.0),
            ),
            tags = listOf("chaos", "ailment", "damage")
        ),
        tabled(
            code = "INCREASED_BLEED_DAMAGE", source = PREFIX,
            stats = listOf(STOCK_BLEED_DAMAGE to ADD),
            tiers = listOf(
                tier(70, 41.0..50.0),
                tier(50, 31.0..40.0),
                tier(30, 21.0..30.0),
                tier(12, 11.0..20.0),
            ),
            tags = listOf("physical", "ailment", "damage")
        ),
        // Защита от состояний и оглушения
        tabled(
            code = "AVOID_IGNITE", source = SUFFIX,
            stats = listOf(STOCK_AVOID_IGNITE to ADD),
            tiers = listOf(
                tier(60, 36.0..45.0),
                tier(35, 26.0..35.0),
                tier(10, 16.0..25.0),
            ),
            tags = listOf("fire", "ailment", "defences")
        ),
        tabled(
            code = "AVOID_CHILL", source = SUFFIX,
            stats = listOf(STOCK_AVOID_CHILL to ADD),
            tiers = listOf(
                tier(60, 36.0..45.0),
                tier(35, 26.0..35.0),
                tier(10, 16.0..25.0),
            ),
            tags = listOf("cold", "ailment", "defences")
        ),
        tabled(
            code = "AVOID_FREEZE", source = SUFFIX,
            stats = listOf(STOCK_AVOID_FREEZE to ADD),
            tiers = listOf(
                tier(60, 36.0..45.0),
                tier(35, 26.0..35.0),
                tier(10, 16.0..25.0),
            ),
            tags = listOf("cold", "ailment", "defences")
        ),
        tabled(
            code = "AVOID_SHOCK", source = SUFFIX,
            stats = listOf(STOCK_AVOID_SHOCK to ADD),
            tiers = listOf(
                tier(60, 36.0..45.0),
                tier(35, 26.0..35.0),
                tier(10, 16.0..25.0),
            ),
            tags = listOf("lightning", "ailment", "defences")
        ),
        tabled(
            code = "AVOID_POISON", source = SUFFIX,
            stats = listOf(STOCK_AVOID_POISON to ADD),
            tiers = listOf(
                tier(60, 36.0..45.0),
                tier(35, 26.0..35.0),
                tier(10, 16.0..25.0),
            ),
            tags = listOf("chaos", "ailment", "defences")
        ),
        tabled(
            code = "AVOID_BLEED", source = SUFFIX,
            stats = listOf(STOCK_AVOID_BLEED to ADD),
            tiers = listOf(
                tier(60, 36.0..45.0),
                tier(35, 26.0..35.0),
                tier(10, 16.0..25.0),
            ),
            tags = listOf("physical", "ailment", "defences")
        ),
        tabled(
            code = "AVOID_STUN", source = SUFFIX,
            stats = listOf(STOCK_AVOID_STUN to ADD),
            tiers = listOf(
                tier(60, 26.0..35.0),
                tier(30, 16.0..25.0),
                tier(5, 10.0..15.0),
            ),
            tags = listOf("stun", "defences")
        ),
        tabled(
            code = "REDUCED_IGNITE_DURATION_ON_SELF", source = SUFFIX,
            stats = listOf(STOCK_IGNITE_DURATION_ON_SELF to ADD),
            tiers = listOf(
                tier(70, 51.0..60.0),
                tier(45, 41.0..50.0),
                tier(20, 31.0..40.0),
            ),
            tags = listOf("fire", "ailment", "defences")
        ),
        tabled(
            code = "REDUCED_CHILL_DURATION_ON_SELF", source = SUFFIX,
            stats = listOf(STOCK_CHILL_DURATION_ON_SELF to ADD),
            tiers = listOf(
                tier(70, 51.0..60.0),
                tier(45, 41.0..50.0),
                tier(20, 31.0..40.0),
            ),
            tags = listOf("cold", "ailment", "defences")
        ),
        tabled(
            code = "REDUCED_FREEZE_DURATION_ON_SELF", source = SUFFIX,
            stats = listOf(STOCK_FREEZE_DURATION_ON_SELF to ADD),
            tiers = listOf(
                tier(70, 51.0..60.0),
                tier(45, 41.0..50.0),
                tier(20, 31.0..40.0),
            ),
            tags = listOf("cold", "ailment", "defences")
        ),
        tabled(
            code = "REDUCED_SHOCK_DURATION_ON_SELF", source = SUFFIX,
            stats = listOf(STOCK_SHOCK_DURATION_ON_SELF to ADD),
            tiers = listOf(
                tier(70, 51.0..60.0),
                tier(45, 41.0..50.0),
                tier(20, 31.0..40.0),
            ),
            tags = listOf("lightning", "ailment", "defences")
        ),
        tabled(
            code = "REDUCED_POISON_DURATION_ON_SELF", source = SUFFIX,
            stats = listOf(STOCK_POISON_DURATION_ON_SELF to ADD),
            tiers = listOf(
                tier(70, 51.0..60.0),
                tier(45, 41.0..50.0),
                tier(20, 31.0..40.0),
            ),
            tags = listOf("chaos", "ailment", "defences")
        ),
        tabled(
            code = "REDUCED_BLEED_DURATION_ON_SELF", source = SUFFIX,
            stats = listOf(STOCK_BLEED_DURATION_ON_SELF to ADD),
            tiers = listOf(
                tier(70, 51.0..60.0),
                tier(45, 41.0..50.0),
                tier(20, 31.0..40.0),
            ),
            tags = listOf("physical", "ailment", "defences")
        ),
        tabled(
            code = "ADD_SPELL_BLOCK_CHANCE", source = SUFFIX,
            stats = listOf(STOCK_SPELL_BLOCK to ADD),
            tiers = listOf(
                tier(70, 10.0..12.0),
                tier(45, 7.0..9.0),
                tier(16, 4.0..6.0),
            ),
            tags = listOf("block", "defences")
        ),
        tabled(
            code = "ADD_PHYSICAL_DAMAGE_REDUCTION", source = SUFFIX,
            stats = listOf(STOCK_PHYSICAL_REDUCTION to ADD),
            tiers = listOf(
                tier(75, 5.0..6.0),
                tier(50, 3.0..4.0),
                tier(25, 1.0..2.0),
            ),
            tags = listOf("physical", "defences"), weight = 300
        ),
        tabled(
            code = "ADD_MAXIMUM_FIRE_RESISTANCE", source = SUFFIX,
            stats = listOf(STOCK_RESIST_MAX_FIRE to ADD),
            tiers = listOf(
                tier(80, 3.0..3.0),
                tier(60, 2.0..2.0),
                tier(40, 1.0..1.0),
            ),
            tags = listOf("fire", "resistance"), weight = 250
        ),
        tabled(
            code = "ADD_MAXIMUM_COLD_RESISTANCE", source = SUFFIX,
            stats = listOf(STOCK_RESIST_MAX_COLD to ADD),
            tiers = listOf(
                tier(80, 3.0..3.0),
                tier(60, 2.0..2.0),
                tier(40, 1.0..1.0),
            ),
            tags = listOf("cold", "resistance"), weight = 250
        ),
        tabled(
            code = "ADD_MAXIMUM_LIGHTNING_RESISTANCE", source = SUFFIX,
            stats = listOf(STOCK_RESIST_MAX_LIGHTNING to ADD),
            tiers = listOf(
                tier(80, 3.0..3.0),
                tier(60, 2.0..2.0),
                tier(40, 1.0..1.0),
            ),
            tags = listOf("lightning", "resistance"), weight = 250
        ),
        tabled(
            code = "ADD_MAXIMUM_CHAOS_RESISTANCE", source = SUFFIX,
            stats = listOf(STOCK_RESIST_MAX_CHAOS to ADD),
            tiers = listOf(
                tier(80, 3.0..3.0),
                tier(60, 2.0..2.0),
                tier(40, 1.0..1.0),
            ),
            tags = listOf("chaos", "resistance"), weight = 250
        ),
        tabled(
            code = "ADD_MAXIMUM_ELEMENTAL_RESISTANCES", source = SUFFIX,
            stats = listOf(STOCK_RESIST_MAX_ALL to ADD),
            tiers = listOf(
                tier(86, 2.0..2.0),
                tier(70, 1.0..1.0),
            ),
            tags = listOf("elemental", "resistance"), weight = 100
        ),
        // Флакон и восстановление в бою
        tabled(
            code = "ADD_FLASK_CHARGES", source = SUFFIX,
            stats = listOf(STOCK_FLASK_CHARGES to ADD),
            tiers = listOf(
                tier(60, 2.0..2.0),
                tier(25, 1.0..1.0),
            ),
            tags = listOf("flask")
        ),
        tabled(
            code = "INCREASED_FLASK_RECOVERY", source = SUFFIX,
            stats = listOf(STOCK_FLASK_RECOVERY to ADD),
            tiers = listOf(
                tier(60, 31.0..40.0),
                tier(35, 21.0..30.0),
                tier(10, 11.0..20.0),
            ),
            tags = listOf("flask", "life")
        ),
        tabled(
            code = "ADD_LIFE_ON_KILL", source = SUFFIX,
            stats = listOf(STOCK_HEALTH_ON_KILL to ADD),
            tiers = listOf(
                tier(68, 19.0..22.0),
                tier(48, 15.0..18.0),
                tier(30, 11.0..14.0),
                tier(14, 7.0..10.0),
                tier(1, 3.0..6.0),
            ),
            tags = listOf("life")
        ),
        tabled(
            code = "ADD_MANA_ON_KILL", source = SUFFIX,
            stats = listOf(STOCK_MANA_ON_KILL to ADD),
            tiers = listOf(
                tier(45, 5.0..6.0),
                tier(20, 3.0..4.0),
                tier(1, 1.0..2.0),
            ),
            tags = listOf("mana")
        ),
        tabled(
            code = "ADD_LIFE_ON_HIT", source = SUFFIX,
            stats = listOf(STOCK_HEALTH_ON_HIT to ADD),
            tiers = listOf(
                tier(50, 4.0..5.0),
                tier(30, 3.0..3.0),
                tier(11, 2.0..2.0),
            ),
            tags = listOf("life", "attack")
        ),
        tabled(
            code = "ADD_MANA_ON_HIT", source = SUFFIX,
            stats = listOf(STOCK_MANA_ON_HIT to ADD),
            tiers = listOf(
                tier(30, 2.0..2.0),
                tier(5, 1.0..1.0),
            ),
            tags = listOf("mana", "attack")
        ),
        // Свои (не из POE): кампания и карта - свет, сундуки, золото вместе с добычей
        tabled(
            code = "INCREASED_LIGHT_RADIUS_AND_MOVEMENT_SPEED", source = SUFFIX,
            stats = listOf(STOCK_LIGHT_RADIUS to INCREASED, STOCK_MOVEMENT_SPEED to INCREASED),
            tiers = listOf(
                tier(70, 12.0..15.0, 8.0..10.0),
                tier(40, 8.0..11.0, 5.0..7.0),
                tier(12, 5.0..7.0, 3.0..4.0),
            ),
            tags = listOf("light", "speed"), weight = 500
        ),
        tabled(
            code = "INCREASED_CHEST_QUANTITY_AND_ITEM_RARITY", source = SUFFIX,
            stats = listOf(STOCK_CHEST_QUANTITY to INCREASED, STOCK_RARITY to INCREASED),
            tiers = listOf(
                tier(75, 18.0..20.0, 10.0..12.0),
                tier(50, 12.0..15.0, 7.0..9.0),
                tier(25, 6.0..9.0, 4.0..6.0),
            ),
            tags = listOf("quantity", "rarity"), weight = 300
        ),
        tabled(
            code = "INCREASED_SELL_VALUE_AND_ITEM_QUANTITY", source = SUFFIX,
            stats = listOf(STOCK_GOLD to INCREASED, STOCK_QUANTITY to INCREASED),
            tiers = listOf(
                tier(75, 15.0..18.0, 5.0..6.0),
                tier(50, 10.0..13.0, 3.0..4.0),
                tier(25, 5.0..8.0, 1.0..2.0),
            ),
            tags = listOf("gold", "quantity"), weight = 300
        ),
        // Свои: обоюдоострые - сильный плюс ценой постоянного минуса, минус не растёт с тиром
        tabled(
            code = "RISK_PHYSICAL_DAMAGE_FOR_LIFE", source = PREFIX,
            stats = listOf(STOCK_ATTACK_PHYSICAL to INCREASED, STOCK_HEALTH to ADD),
            tiers = listOf(
                tier(80, 40.0..49.0, -30.0..-30.0),
                tier(55, 30.0..39.0, -30.0..-30.0),
                tier(30, 20.0..29.0, -30.0..-30.0),
            ),
            tags = listOf("physical", "damage", "risk"), weight = 400
        ),
        tabled(
            code = "RISK_ATTACK_SPEED_FOR_RESISTANCES", source = SUFFIX,
            stats = listOf(STOCK_ATTACK_SPEED to INCREASED, STOCK_RESIST_ALL to ADD),
            tiers = listOf(
                tier(75, 16.0..18.0, -10.0..-10.0),
                tier(50, 12.0..15.0, -10.0..-10.0),
                tier(25, 8.0..11.0, -10.0..-10.0),
            ),
            tags = listOf("attack", "speed", "risk"), weight = 400
        ),
        tabled(
            code = "RISK_SPELL_DAMAGE_FOR_CHAOS_RESISTANCE", source = PREFIX,
            stats = listOf(STOCK_ATTACK_MAGICAL to INCREASED, STOCK_RESIST_CHAOS to ADD),
            tiers = listOf(
                tier(80, 50.0..59.0, -20.0..-20.0),
                tier(55, 35.0..49.0, -20.0..-20.0),
                tier(30, 20.0..34.0, -20.0..-20.0),
            ),
            tags = listOf("caster", "damage", "risk"), weight = 400
        ),
        tabled(
            code = "RISK_BLOCK_FOR_CRITICAL_MULTIPLIER", source = SUFFIX,
            stats = listOf(STOCK_BLOCK_CHANCE to ADD, STOCK_CRITICAL_MULTIPLIER to ADD),
            tiers = listOf(
                tier(70, 6.0..7.0, -15.0..-15.0),
                tier(40, 4.0..5.0, -15.0..-15.0),
                tier(15, 2.0..3.0, -15.0..-15.0),
            ),
            tags = listOf("block", "defences", "risk"), weight = 400
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

package config

import application.enums.EnumEquipmentType
import application.enums.EnumEquipmentType.AMULET
import application.enums.EnumEquipmentType.BELT
import application.enums.EnumEquipmentType.BODY
import application.enums.EnumEquipmentType.BOOTS
import application.enums.EnumEquipmentType.GLOVES
import application.enums.EnumEquipmentType.HELMET
import application.enums.EnumEquipmentType.QUIVER
import application.enums.EnumEquipmentType.RING
import application.enums.EnumEquipmentType.SHIELD
import application.enums.EnumEquipmentType.WEAPON_1H
import application.enums.EnumEquipmentType.WEAPON_2H
import application.enums.EnumEquipmentType.WINGS
import application.enums.EnumEquipmentWeapon
import application.enums.EnumEquipmentWeapon.BLADE
import application.enums.EnumEquipmentWeapon.BOW
import application.enums.EnumEquipmentWeapon.DOUBLEAXE
import application.enums.EnumEquipmentWeapon.DOUBLESWORD
import application.enums.EnumEquipmentWeapon.LONGSWORD
import application.enums.EnumEquipmentWeapon.WAND
import application.enums.EnumModifierOperation.ADD
import application.enums.EnumModifierOperation.INCREASED
import application.enums.EnumModifierOperation.MORE
import application.enums.EnumModifierSource
import application.enums.EnumRarity
import application.enums.EnumStatStock.STOCK_AGILITY
import application.enums.EnumStatStock.STOCK_ARMOR
import application.enums.EnumStatStock.STOCK_ATTACK_COLD
import application.enums.EnumStatStock.STOCK_ATTACK_FIRE
import application.enums.EnumStatStock.STOCK_ATTACK_LIGHTNING
import application.enums.EnumStatStock.STOCK_ATTACK_MAGICAL
import application.enums.EnumStatStock.STOCK_ATTACK_PHYSICAL
import application.enums.EnumStatStock.STOCK_ATTACK_SPEED
import application.enums.EnumStatStock.STOCK_BLOCK_CHANCE
import application.enums.EnumStatStock.STOCK_CRITICAL_CHANCE
import application.enums.EnumStatStock.STOCK_CRITICAL_MULTIPLIER
import application.enums.EnumStatStock.STOCK_ENERGY_SHIELD
import application.enums.EnumStatStock.STOCK_EVASION
import application.enums.EnumStatStock.STOCK_HEALTH
import application.enums.EnumStatStock.STOCK_HEALTH_REGEN
import application.enums.EnumStatStock.STOCK_INTELLECT
import application.enums.EnumStatStock.STOCK_LEECH_PHYSICAL
import application.enums.EnumStatStock.STOCK_MANA
import application.enums.EnumStatStock.STOCK_MOVEMENT_SPEED
import application.enums.EnumStatStock.STOCK_QUANTITY
import application.enums.EnumStatStock.STOCK_RARITY
import application.enums.EnumStatStock.STOCK_RESIST_ALL
import application.enums.EnumStatStock.STOCK_RESIST_CHAOS
import application.enums.EnumStatStock.STOCK_RESIST_COLD
import application.enums.EnumStatStock.STOCK_RESIST_FIRE
import application.enums.EnumStatStock.STOCK_RESIST_LIGHTNING
import application.enums.EnumStatStock.STOCK_STRENGTH
import features.data.equipment.equipment_data.Accessory
import features.data.equipment.equipment_data.Armor
import features.data.equipment.equipment_data.Equipment
import features.data.equipment.equipment_data.Weapon
import features.logic.modifiers.ModifierDefinition
import features.logic.modifiers.ModifierTier

/**
 * Уникальные предметы - по 3 на каждый слот экипировки.
 *
 * Механика как в POE: у уникалки нет случайных префиксов и суффиксов
 * ([EnumRarity.UNIQUE] роллит 0 и 0), вместо них закреплённый набор
 * модификаторов с источником [EnumModifierSource.UNIQUE]. Значения внутри
 * заданных диапазонов роллятся при получении предмета, поэтому две копии
 * одной уникалки отличаются друг от друга.
 *
 * Названия и наборы модификаторов взяты из POE и упрощены под статы проекта.
 * Слот WINGS в POE отсутствует, предметы для него придуманы в том же стиле.
 */
object UniqueEquipmentSeeder {

    /**
     * Одна строка модификаторов уникального предмета.
     *
     * Несколько эффектов = составной модификатор ("+# to all Attributes").
     */
    private data class UniqueModifier(
        val name: String,
        val effects: List<EffectTemplate>,
    )

    private fun line(name: String, vararg effects: EffectTemplate) = UniqueModifier(name, effects.toList())

    private data class UniqueTemplate(
        val name: String,
        val slot: EnumEquipmentType,
        val itemLevel: Int,
        val description: String,
        val modifiers: List<UniqueModifier>,

        /**
         * Защита брони. Для оружия и аксессуаров не используется.
         */
        val defense: Int = 0,

        val weaponType: EnumEquipmentWeapon? = null,
        val damageMin: Double = 0.0,
        val damageMax: Double = 0.0,
        val attackSpeed: Double = 1.0,
        val durability: Int = 100,
    )

    // ==================== Таблица уникальных предметов ====================

    private val templates = listOf(

        // ---------- HELMET ----------
        UniqueTemplate(
            name = "Goldrim", slot = HELMET, itemLevel = 1, defense = 27,
            description = "No metal slips from the hands of the greedy.",
            modifiers = listOf(
                line("+#% to all Elemental Resistances", effect(STOCK_RESIST_ALL, ADD, 30.0..40.0)),
                line("#% increased Rarity of Items found", effect(STOCK_RARITY, INCREASED, 6.0..15.0)),
                line("+# to Evasion Rating", effect(STOCK_EVASION, ADD, 30.0..30.0)),
            )
        ),
        UniqueTemplate(
            name = "Starkonja's Head", slot = HELMET, itemLevel = 60, defense = 288,
            description = "The greatest of the Karui warriors never left the battlefield.",
            modifiers = listOf(
                line("+# to Dexterity", effect(STOCK_AGILITY, ADD, 50.0..50.0)),
                line("+# to maximum Life", effect(STOCK_HEALTH, ADD, 60.0..80.0)),
                line("#% increased Evasion Rating", effect(STOCK_EVASION, INCREASED, 100.0..150.0)),
                line("#% increased Critical Strike Chance", effect(STOCK_CRITICAL_CHANCE, INCREASED, 25.0..25.0)),
            )
        ),
        UniqueTemplate(
            name = "Devoto's Devotion", slot = HELMET, itemLevel = 62, defense = 296,
            description = "A lifetime of service, ended in a moment of doubt.",
            modifiers = listOf(
                line("+# to Dexterity", effect(STOCK_AGILITY, ADD, 60.0..65.0)),
                line("#% increased Attack Speed", effect(STOCK_ATTACK_SPEED, INCREASED, 10.0..10.0)),
                line("#% increased Movement Speed", effect(STOCK_MOVEMENT_SPEED, INCREASED, 16.0..16.0)),
                line("+#% to Chaos Resistance", effect(STOCK_RESIST_CHAOS, ADD, 20.0..30.0)),
            )
        ),

        // ---------- BODY ----------
        UniqueTemplate(
            name = "Belly of the Beast", slot = BODY, itemLevel = 50, defense = 500,
            description = "Swallowed whole, digested slowly.",
            modifiers = listOf(
                line("#% increased maximum Life", effect(STOCK_HEALTH, INCREASED, 30.0..40.0)),
                line("+#% to all Elemental Resistances", effect(STOCK_RESIST_ALL, ADD, 20.0..30.0)),
                line("#% increased Armour", effect(STOCK_ARMOR, INCREASED, 100.0..150.0)),
            )
        ),
        UniqueTemplate(
            name = "Kaom's Heart", slot = BODY, itemLevel = 68, defense = 500,
            description = "Ten thousand lives for ten thousand nails.",
            modifiers = listOf(
                line("+# to maximum Life", effect(STOCK_HEALTH, ADD, 500.0..500.0)),
                line("#% more Fire Damage", effect(STOCK_ATTACK_FIRE, MORE, 20.0..40.0)),
            )
        ),
        UniqueTemplate(
            name = "Carcass Jack", slot = BODY, itemLevel = 62, defense = 350,
            description = "A patchwork of the fallen, stitched by the living.",
            modifiers = listOf(
                line(
                    "#% increased Evasion and Energy Shield",
                    effect(STOCK_EVASION, INCREASED, 120.0..150.0),
                    effect(STOCK_ENERGY_SHIELD, INCREASED, 120.0..150.0),
                ),
                line("+#% to all Elemental Resistances", effect(STOCK_RESIST_ALL, ADD, 12.0..16.0)),
                line("+# to maximum Life", effect(STOCK_HEALTH, ADD, 50.0..70.0)),
            )
        ),

        // ---------- GLOVES ----------
        UniqueTemplate(
            name = "Facebreaker", slot = GLOVES, itemLevel = 16, defense = 0,
            description = "Who needs a weapon when you have knuckles?",
            modifiers = listOf(
                line("#% increased Physical Damage", effect(STOCK_ATTACK_PHYSICAL, INCREASED, 600.0..800.0)),
                line("+#% to Chaos Resistance", effect(STOCK_RESIST_CHAOS, ADD, 2.0..4.0)),
            )
        ),
        UniqueTemplate(
            name = "Maligaro's Virtuosity", slot = GLOVES, itemLevel = 21, defense = 40,
            description = "The pain of others was his only art.",
            modifiers = listOf(
                line("+#% to Critical Strike Multiplier", effect(STOCK_CRITICAL_MULTIPLIER, ADD, 20.0..30.0)),
                line("#% increased Critical Strike Chance", effect(STOCK_CRITICAL_CHANCE, INCREASED, 20.0..30.0)),
                line("#% increased Attack Speed", effect(STOCK_ATTACK_SPEED, INCREASED, 10.0..16.0)),
            )
        ),
        UniqueTemplate(
            name = "Atziri's Acuity", slot = GLOVES, itemLevel = 68, defense = 350,
            description = "Her touch was precise, and always fatal.",
            modifiers = listOf(
                line("+# to Dexterity", effect(STOCK_AGILITY, ADD, 40.0..50.0)),
                line("+# to maximum Life", effect(STOCK_HEALTH, ADD, 60.0..80.0)),
                line(
                    "#% increased Armour and Evasion",
                    effect(STOCK_ARMOR, INCREASED, 150.0..200.0),
                    effect(STOCK_EVASION, INCREASED, 150.0..200.0),
                ),
            )
        ),

        // ---------- BOOTS ----------
        UniqueTemplate(
            name = "Wanderlust", slot = BOOTS, itemLevel = 1, defense = 0,
            description = "The journey matters more than the destination.",
            modifiers = listOf(
                line("+# to Dexterity", effect(STOCK_AGILITY, ADD, 5.0..10.0)),
                line("+# to maximum Mana", effect(STOCK_MANA, ADD, 20.0..30.0)),
                line("#% increased Movement Speed", effect(STOCK_MOVEMENT_SPEED, INCREASED, 20.0..20.0)),
            )
        ),
        UniqueTemplate(
            name = "Seven-League Step", slot = BOOTS, itemLevel = 20, defense = 0,
            description = "Some go far. Some go fast. A few do both.",
            modifiers = listOf(
                line("#% increased Movement Speed", effect(STOCK_MOVEMENT_SPEED, INCREASED, 50.0..50.0)),
            )
        ),
        UniqueTemplate(
            name = "Goldwyrm", slot = BOOTS, itemLevel = 40, defense = 60,
            description = "Wealth burns brighter than any flame.",
            modifiers = listOf(
                line("+# to maximum Mana", effect(STOCK_MANA, ADD, 20.0..30.0)),
                line("+#% to Fire Resistance", effect(STOCK_RESIST_FIRE, ADD, 20.0..30.0)),
                line("#% increased Movement Speed", effect(STOCK_MOVEMENT_SPEED, INCREASED, 20.0..20.0)),
                line("#% increased Rarity of Items found", effect(STOCK_RARITY, INCREASED, 30.0..40.0)),
            )
        ),

        // ---------- WINGS (слота нет в POE, предметы придуманы в его стиле) ----------
        UniqueTemplate(
            name = "Wings of Vastiri", slot = WINGS, itemLevel = 40, defense = 80,
            description = "The desert wind never asked permission to pass.",
            modifiers = listOf(
                line("#% increased Movement Speed", effect(STOCK_MOVEMENT_SPEED, INCREASED, 10.0..15.0)),
                line("+# to Dexterity", effect(STOCK_AGILITY, ADD, 20.0..30.0)),
            )
        ),
        UniqueTemplate(
            name = "Shroud of the Seventh", slot = WINGS, itemLevel = 60, defense = 120,
            description = "Six fell before it. The seventh learned to fly.",
            modifiers = listOf(
                line("+# to maximum Energy Shield", effect(STOCK_ENERGY_SHIELD, ADD, 60.0..80.0)),
                line("+#% to all Elemental Resistances", effect(STOCK_RESIST_ALL, ADD, 10.0..15.0)),
            )
        ),
        UniqueTemplate(
            name = "Pinion of the Maw", slot = WINGS, itemLevel = 70, defense = 150,
            description = "Torn from something that should not have had wings.",
            modifiers = listOf(
                line("+# to maximum Life", effect(STOCK_HEALTH, ADD, 60.0..80.0)),
                line(
                    "#% increased Armour and Evasion",
                    effect(STOCK_ARMOR, INCREASED, 80.0..120.0),
                    effect(STOCK_EVASION, INCREASED, 80.0..120.0),
                ),
            )
        ),

        // ---------- BELT ----------
        UniqueTemplate(
            name = "Meginord's Girdle", slot = BELT, itemLevel = 20,
            description = "The strength of the Karui is measured in iron.",
            modifiers = listOf(
                line("+# to Strength", effect(STOCK_STRENGTH, ADD, 25.0..30.0)),
                line("Adds # Physical Damage", effect(STOCK_ATTACK_PHYSICAL, ADD, 5.0..7.0)),
                line("+#% to Cold Resistance", effect(STOCK_RESIST_COLD, ADD, 20.0..30.0)),
                line("Regenerate # Life per second", effect(STOCK_HEALTH_REGEN, ADD, 8.0..12.0)),
            )
        ),
        UniqueTemplate(
            name = "Headhunter", slot = BELT, itemLevel = 40,
            description = "Beneath the belt, a hundred names. Above it, one.",
            modifiers = listOf(
                line(
                    "+# to Strength and Dexterity",
                    effect(STOCK_STRENGTH, ADD, 40.0..55.0),
                    effect(STOCK_AGILITY, ADD, 40.0..55.0),
                ),
                line("+# to maximum Life", effect(STOCK_HEALTH, ADD, 50.0..70.0)),
            )
        ),
        UniqueTemplate(
            name = "The Magnate", slot = BELT, itemLevel = 20,
            description = "Gold buys strength. Strength keeps gold.",
            modifiers = listOf(
                line("+# to Strength", effect(STOCK_STRENGTH, ADD, 25.0..40.0)),
                line("+#% to Fire Resistance", effect(STOCK_RESIST_FIRE, ADD, 20.0..30.0)),
                line("#% increased Rarity of Items found", effect(STOCK_RARITY, INCREASED, 10.0..20.0)),
            )
        ),

        // ---------- RING ----------
        UniqueTemplate(
            name = "Berek's Grip", slot = RING, itemLevel = 20,
            description = "Berek held the storm, and the storm held Berek.",
            modifiers = listOf(
                line("+# to maximum Life", effect(STOCK_HEALTH, ADD, 20.0..30.0)),
                line(
                    "+#% to Cold and Lightning Resistances",
                    effect(STOCK_RESIST_COLD, ADD, 20.0..30.0),
                    effect(STOCK_RESIST_LIGHTNING, ADD, 20.0..30.0),
                ),
                line("#% of Physical Attack Damage Leeched as Life", effect(STOCK_LEECH_PHYSICAL, ADD, 0.4..0.8)),
            )
        ),
        UniqueTemplate(
            name = "Ventor's Gamble", slot = RING, itemLevel = 65,
            description = "Fortune favours those who can afford to lose.",
            modifiers = listOf(
                line("#% increased Rarity of Items found", effect(STOCK_RARITY, INCREASED, 20.0..50.0)),
                line("#% increased Quantity of Items found", effect(STOCK_QUANTITY, INCREASED, 10.0..20.0)),
                line("+# to maximum Life", effect(STOCK_HEALTH, ADD, 10.0..20.0)),
                line("+#% to all Elemental Resistances", effect(STOCK_RESIST_ALL, ADD, 10.0..30.0)),
            )
        ),
        UniqueTemplate(
            name = "Kaom's Sign", slot = RING, itemLevel = 40,
            description = "A ring of iron for a king of ash.",
            modifiers = listOf(
                line("+# to Strength", effect(STOCK_STRENGTH, ADD, 15.0..25.0)),
                line("+#% to Fire Resistance", effect(STOCK_RESIST_FIRE, ADD, 20.0..30.0)),
                line("Regenerate # Life per second", effect(STOCK_HEALTH_REGEN, ADD, 5.0..10.0)),
            )
        ),

        // ---------- AMULET ----------
        UniqueTemplate(
            name = "Astramentis", slot = AMULET, itemLevel = 30,
            description = "The stars gave freely, and asked for everything.",
            modifiers = listOf(
                line(
                    "+# to all Attributes",
                    effect(STOCK_STRENGTH, ADD, 80.0..100.0),
                    effect(STOCK_AGILITY, ADD, 80.0..100.0),
                    effect(STOCK_INTELLECT, ADD, 80.0..100.0),
                ),
            )
        ),
        UniqueTemplate(
            name = "Carnage Heart", slot = AMULET, itemLevel = 40,
            description = "It still beats, and it still hungers.",
            modifiers = listOf(
                line(
                    "+# to all Attributes",
                    effect(STOCK_STRENGTH, ADD, 20.0..30.0),
                    effect(STOCK_AGILITY, ADD, 20.0..30.0),
                    effect(STOCK_INTELLECT, ADD, 20.0..30.0),
                ),
                line("#% of Physical Attack Damage Leeched as Life", effect(STOCK_LEECH_PHYSICAL, ADD, 0.6..1.0)),
                line("+#% to all Elemental Resistances", effect(STOCK_RESIST_ALL, ADD, 8.0..14.0)),
            )
        ),
        UniqueTemplate(
            name = "Bisco's Collar", slot = AMULET, itemLevel = 50,
            description = "A good dog always brings something back.",
            modifiers = listOf(
                line("#% increased Rarity of Items found", effect(STOCK_RARITY, INCREASED, 50.0..100.0)),
                line("#% increased Quantity of Items found", effect(STOCK_QUANTITY, INCREASED, 20.0..30.0)),
            )
        ),

        // ---------- SHIELD ----------
        UniqueTemplate(
            name = "Lioneye's Remorse", slot = SHIELD, itemLevel = 45, defense = 800,
            description = "Marceus stood his ground, and the ground gave way.",
            modifiers = listOf(
                line("+# to maximum Life", effect(STOCK_HEALTH, ADD, 100.0..120.0)),
                line("#% increased Armour", effect(STOCK_ARMOR, INCREASED, 200.0..250.0)),
                line("+#% Chance to Block", effect(STOCK_BLOCK_CHANCE, ADD, 5.0..8.0)),
            )
        ),
        UniqueTemplate(
            name = "Rise of the Phoenix", slot = SHIELD, itemLevel = 60, defense = 400,
            description = "From the ashes, again and again and again.",
            modifiers = listOf(
                line("+# to maximum Life", effect(STOCK_HEALTH, ADD, 60.0..80.0)),
                line("+#% to Fire Resistance", effect(STOCK_RESIST_FIRE, ADD, 40.0..50.0)),
                line("#% increased Armour", effect(STOCK_ARMOR, INCREASED, 150.0..200.0)),
                line("Regenerate # Life per second", effect(STOCK_HEALTH_REGEN, ADD, 10.0..20.0)),
            )
        ),
        UniqueTemplate(
            name = "Saffell's Frame", slot = SHIELD, itemLevel = 45, defense = 120,
            description = "The finest defence is one the enemy never reaches.",
            modifiers = listOf(
                line("+#% to all Elemental Resistances", effect(STOCK_RESIST_ALL, ADD, 20.0..25.0)),
                line("#% increased Spell Damage", effect(STOCK_ATTACK_MAGICAL, INCREASED, 20.0..40.0)),
                line("+#% Chance to Block", effect(STOCK_BLOCK_CHANCE, ADD, 5.0..8.0)),
            )
        ),

        // ---------- QUIVER ----------
        UniqueTemplate(
            name = "Drillneck", slot = QUIVER, itemLevel = 40,
            description = "Armour is just another thing to go through.",
            modifiers = listOf(
                line("+# to Dexterity", effect(STOCK_AGILITY, ADD, 20.0..30.0)),
                line("#% increased Attack Speed", effect(STOCK_ATTACK_SPEED, INCREASED, 8.0..12.0)),
                line("#% increased Physical Damage", effect(STOCK_ATTACK_PHYSICAL, INCREASED, 20.0..40.0)),
            )
        ),
        UniqueTemplate(
            name = "Rearguard", slot = QUIVER, itemLevel = 30,
            description = "The last line, carried on your own back.",
            modifiers = listOf(
                line("+# to maximum Energy Shield", effect(STOCK_ENERGY_SHIELD, ADD, 30.0..50.0)),
                line("+# to maximum Life", effect(STOCK_HEALTH, ADD, 40.0..60.0)),
                line("+#% Chance to Block", effect(STOCK_BLOCK_CHANCE, ADD, 3.0..5.0)),
            )
        ),
        UniqueTemplate(
            name = "Hyrri's Bite", slot = QUIVER, itemLevel = 45,
            description = "Cold arrows for a colder heart.",
            modifiers = listOf(
                line("+# to Dexterity", effect(STOCK_AGILITY, ADD, 20.0..30.0)),
                line("Adds # Cold Damage", effect(STOCK_ATTACK_COLD, ADD, 15.0..25.0)),
                line("#% of Physical Attack Damage Leeched as Life", effect(STOCK_LEECH_PHYSICAL, ADD, 0.4..0.8)),
            )
        ),

        // ---------- WEAPON_1H ----------
        UniqueTemplate(
            name = "Bino's Kitchen Knife", slot = WEAPON_1H, itemLevel = 50,
            weaponType = BLADE, damageMin = 40.0, damageMax = 90.0, attackSpeed = 1.4, durability = 120,
            description = "Bino always kept his kitchen spotless.",
            modifiers = listOf(
                line("#% increased Critical Strike Chance", effect(STOCK_CRITICAL_CHANCE, INCREASED, 30.0..40.0)),
                line("+# to maximum Life", effect(STOCK_HEALTH, ADD, 20.0..30.0)),
                line("#% increased Physical Damage", effect(STOCK_ATTACK_PHYSICAL, INCREASED, 50.0..70.0)),
            )
        ),
        UniqueTemplate(
            name = "Doryani's Catalyst", slot = WEAPON_1H, itemLevel = 60,
            weaponType = WAND, damageMin = 30.0, damageMax = 70.0, attackSpeed = 1.4, durability = 100,
            description = "The Vaal reached for power, and it reached back.",
            modifiers = listOf(
                line("Adds # Lightning Damage", effect(STOCK_ATTACK_LIGHTNING, ADD, 10.0..80.0)),
                line("#% increased Spell Damage", effect(STOCK_ATTACK_MAGICAL, INCREASED, 40.0..60.0)),
                line("#% of Physical Attack Damage Leeched as Life", effect(STOCK_LEECH_PHYSICAL, ADD, 0.4..0.8)),
            )
        ),
        UniqueTemplate(
            name = "Lioneye's Glare", slot = WEAPON_1H, itemLevel = 70,
            weaponType = BOW, damageMin = 120.0, damageMax = 250.0, attackSpeed = 1.4, durability = 140,
            description = "Every arrow finds its mark, whether it deserves to or not.",
            modifiers = listOf(
                line("#% increased Physical Damage", effect(STOCK_ATTACK_PHYSICAL, INCREASED, 150.0..200.0)),
                line("#% increased Attack Speed", effect(STOCK_ATTACK_SPEED, INCREASED, 10.0..14.0)),
                line("+# to Dexterity", effect(STOCK_AGILITY, ADD, 30.0..40.0)),
            )
        ),

        // ---------- WEAPON_2H ----------
        UniqueTemplate(
            name = "Marohi Erqi", slot = WEAPON_2H, itemLevel = 50,
            weaponType = DOUBLEAXE, damageMin = 150.0, damageMax = 400.0, attackSpeed = 0.8, durability = 200,
            description = "It does not swing quickly. It does not need to.",
            modifiers = listOf(
                line("#% increased Physical Damage", effect(STOCK_ATTACK_PHYSICAL, INCREASED, 250.0..300.0)),
                line("+# to Strength", effect(STOCK_STRENGTH, ADD, 30.0..40.0)),
            )
        ),
        UniqueTemplate(
            name = "Starforge", slot = WEAPON_2H, itemLevel = 75,
            weaponType = DOUBLESWORD, damageMin = 200.0, damageMax = 400.0, attackSpeed = 1.3, durability = 220,
            description = "Forged where stars are born, and where they die.",
            modifiers = listOf(
                line("#% increased Physical Damage", effect(STOCK_ATTACK_PHYSICAL, INCREASED, 300.0..400.0)),
                line("+# to Strength", effect(STOCK_STRENGTH, ADD, 40.0..50.0)),
                line("#% more Physical Damage", effect(STOCK_ATTACK_PHYSICAL, MORE, 10.0..15.0)),
            )
        ),
        UniqueTemplate(
            name = "Hegemony's Era", slot = WEAPON_2H, itemLevel = 65,
            weaponType = LONGSWORD, damageMin = 140.0, damageMax = 300.0, attackSpeed = 1.2, durability = 180,
            description = "An age ends the moment someone decides it should.",
            modifiers = listOf(
                line(
                    "+# to Strength and Dexterity",
                    effect(STOCK_STRENGTH, ADD, 20.0..30.0),
                    effect(STOCK_AGILITY, ADD, 20.0..30.0),
                ),
                line("#% increased Physical Damage", effect(STOCK_ATTACK_PHYSICAL, INCREASED, 150.0..200.0)),
                line("+#% to Critical Strike Multiplier", effect(STOCK_CRITICAL_MULTIPLIER, ADD, 20.0..30.0)),
            )
        ),
    )

    // ==================== Генерация документов ====================

    private fun UniqueTemplate.modifierTemplates(): List<ModifierTemplate> =
        modifiers.mapIndexed { index, modifier ->
            ModifierTemplate(
                code = modifierCode(name, index),
                name = modifier.name,
                source = EnumModifierSource.UNIQUE,
                effects = modifier.effects,
                // У уникалки один тир: диапазон фиксирован самим предметом
                tierCount = 1,
                bestItemLevel = itemLevel,
                tags = listOf("unique", slot.name.lowercase())
            )
        }

    private fun modifierCode(itemName: String, index: Int): String =
        "UNIQUE_${itemName.uppercase().replace(Regex("[^A-Z0-9]+"), "_").trim('_')}_$index"

    private val modifierTemplates: List<ModifierTemplate> = templates.flatMap { it.modifierTemplates() }

    /**
     * Описания модификаторов уникальных предметов.
     */
    fun seedDefinitions(): List<ModifierDefinition> = modifierTemplates.toDefinitions()

    /**
     * Тиры модификаторов уникальных предметов - по одному на модификатор.
     */
    fun seedTiers(definitions: List<ModifierDefinition>): List<ModifierTier> = modifierTemplates.toTiers(definitions)

    /**
     * Шаблоны самих уникальных предметов.
     *
     * @param resolve резолвер кода модификатора в его [ModifierDefinition._id]
     */
    fun seedEquipment(resolve: (String) -> String): List<Equipment> = templates.map { template ->
        val modifierIds = template.modifiers.indices
            .mapTo(mutableListOf()) { resolve(modifierCode(template.name, it)) }

        template.toEquipment(modifierIds)
    }

    private fun UniqueTemplate.toEquipment(modifierIds: MutableList<String>): Equipment = when (slot) {
        WEAPON_1H, WEAPON_2H -> Weapon(
            slot = slot,
            weaponType = weaponType ?: BLADE,
            damage_min = damageMin,
            damage_max = damageMax,
            attackSpeed = attackSpeed,
            durability = durability,
            name = name,
            rarity = EnumRarity.UNIQUE,
            itemLevel = itemLevel,
            description = description,
            modifierIds = modifierIds
        )

        RING, AMULET, BELT, QUIVER -> Accessory(
            slot = slot,
            name = name,
            rarity = EnumRarity.UNIQUE,
            itemLevel = itemLevel,
            description = description,
            modifierIds = modifierIds
        )

        else -> Armor(
            slot = slot,
            defense = defense,
            name = name,
            rarity = EnumRarity.UNIQUE,
            itemLevel = itemLevel,
            description = description,
            modifierIds = modifierIds
        )
    }
}

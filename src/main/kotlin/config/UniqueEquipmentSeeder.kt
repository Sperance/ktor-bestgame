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
import extensions.to1Digits
import features.logic.modifiers.Modifier
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
        val effects: List<EffectTemplate>,
    )

    private fun line(vararg effects: EffectTemplate) = UniqueModifier(effects.toList())

    private data class UniqueTemplate(
        val code: String,
        val slot: EnumEquipmentType,
        val itemLevel: Int,
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
            code = "GOLDRIM", slot = HELMET, itemLevel = 1, defense = 27,
            modifiers = listOf(
                line(effect(STOCK_RESIST_ALL, ADD, 30.0..40.0)),
                line(effect(STOCK_RARITY, INCREASED, 6.0..15.0)),
                line(effect(STOCK_EVASION, ADD, 30.0..30.0)),
            )
        ),
        UniqueTemplate(
            code = "STARKONJA_S_HEAD", slot = HELMET, itemLevel = 60, defense = 288,
            modifiers = listOf(
                line(effect(STOCK_AGILITY, ADD, 50.0..50.0)),
                line(effect(STOCK_HEALTH, ADD, 60.0..80.0)),
                line(effect(STOCK_EVASION, INCREASED, 100.0..150.0)),
                line(effect(STOCK_CRITICAL_CHANCE, INCREASED, 25.0..25.0)),
            )
        ),
        UniqueTemplate(
            code = "DEVOTO_S_DEVOTION", slot = HELMET, itemLevel = 62, defense = 296,
            modifiers = listOf(
                line(effect(STOCK_AGILITY, ADD, 60.0..65.0)),
                line(effect(STOCK_ATTACK_SPEED, INCREASED, 10.0..10.0)),
                line(effect(STOCK_MOVEMENT_SPEED, INCREASED, 16.0..16.0)),
                line(effect(STOCK_RESIST_CHAOS, ADD, 20.0..30.0)),
            )
        ),

        // ---------- BODY ----------
        UniqueTemplate(
            code = "BELLY_OF_THE_BEAST", slot = BODY, itemLevel = 50, defense = 500,
            modifiers = listOf(
                line(effect(STOCK_HEALTH, INCREASED, 30.0..40.0)),
                line(effect(STOCK_RESIST_ALL, ADD, 20.0..30.0)),
                line(effect(STOCK_ARMOR, INCREASED, 100.0..150.0)),
            )
        ),
        UniqueTemplate(
            code = "KAOM_S_HEART", slot = BODY, itemLevel = 68, defense = 500,
            modifiers = listOf(
                line(effect(STOCK_HEALTH, ADD, 500.0..500.0)),
                line(effect(STOCK_ATTACK_FIRE, MORE, 20.0..40.0)),
            )
        ),
        UniqueTemplate(
            code = "CARCASS_JACK", slot = BODY, itemLevel = 62, defense = 350,
            modifiers = listOf(
                line(effect(STOCK_EVASION, INCREASED, 120.0..150.0),
                    effect(STOCK_ENERGY_SHIELD, INCREASED, 120.0..150.0),
                ),
                line(effect(STOCK_RESIST_ALL, ADD, 12.0..16.0)),
                line(effect(STOCK_HEALTH, ADD, 50.0..70.0)),
            )
        ),

        // ---------- GLOVES ----------
        UniqueTemplate(
            code = "FACEBREAKER", slot = GLOVES, itemLevel = 16, defense = 0,
            modifiers = listOf(
                line(effect(STOCK_ATTACK_PHYSICAL, INCREASED, 600.0..800.0)),
                line(effect(STOCK_RESIST_CHAOS, ADD, 2.0..4.0)),
            )
        ),
        UniqueTemplate(
            code = "MALIGARO_S_VIRTUOSITY", slot = GLOVES, itemLevel = 21, defense = 40,
            modifiers = listOf(
                line(effect(STOCK_CRITICAL_MULTIPLIER, ADD, 20.0..30.0)),
                line(effect(STOCK_CRITICAL_CHANCE, INCREASED, 20.0..30.0)),
                line(effect(STOCK_ATTACK_SPEED, INCREASED, 10.0..16.0)),
            )
        ),
        UniqueTemplate(
            code = "ATZIRI_S_ACUITY", slot = GLOVES, itemLevel = 68, defense = 350,
            modifiers = listOf(
                line(effect(STOCK_AGILITY, ADD, 40.0..50.0)),
                line(effect(STOCK_HEALTH, ADD, 60.0..80.0)),
                line(effect(STOCK_ARMOR, INCREASED, 150.0..200.0),
                    effect(STOCK_EVASION, INCREASED, 150.0..200.0),
                ),
            )
        ),

        // ---------- BOOTS ----------
        UniqueTemplate(
            code = "WANDERLUST", slot = BOOTS, itemLevel = 1, defense = 0,
            modifiers = listOf(
                line(effect(STOCK_AGILITY, ADD, 5.0..10.0)),
                line(effect(STOCK_MANA, ADD, 20.0..30.0)),
                line(effect(STOCK_MOVEMENT_SPEED, INCREASED, 20.0..20.0)),
            )
        ),
        UniqueTemplate(
            code = "SEVEN_LEAGUE_STEP", slot = BOOTS, itemLevel = 20, defense = 0,
            modifiers = listOf(
                line(effect(STOCK_MOVEMENT_SPEED, INCREASED, 50.0..50.0)),
            )
        ),
        UniqueTemplate(
            code = "GOLDWYRM", slot = BOOTS, itemLevel = 40, defense = 60,
            modifiers = listOf(
                line(effect(STOCK_MANA, ADD, 20.0..30.0)),
                line(effect(STOCK_RESIST_FIRE, ADD, 20.0..30.0)),
                line(effect(STOCK_MOVEMENT_SPEED, INCREASED, 20.0..20.0)),
                line(effect(STOCK_RARITY, INCREASED, 30.0..40.0)),
            )
        ),

        // ---------- WINGS (слота нет в POE, предметы придуманы в его стиле) ----------
        UniqueTemplate(
            code = "WINGS_OF_VASTIRI", slot = WINGS, itemLevel = 40, defense = 80,
            modifiers = listOf(
                line(effect(STOCK_MOVEMENT_SPEED, INCREASED, 10.0..15.0)),
                line(effect(STOCK_AGILITY, ADD, 20.0..30.0)),
            )
        ),
        UniqueTemplate(
            code = "SHROUD_OF_THE_SEVENTH", slot = WINGS, itemLevel = 60, defense = 120,
            modifiers = listOf(
                line(effect(STOCK_ENERGY_SHIELD, ADD, 60.0..80.0)),
                line(effect(STOCK_RESIST_ALL, ADD, 10.0..15.0)),
            )
        ),
        UniqueTemplate(
            code = "PINION_OF_THE_MAW", slot = WINGS, itemLevel = 70, defense = 150,
            modifiers = listOf(
                line(effect(STOCK_HEALTH, ADD, 60.0..80.0)),
                line(effect(STOCK_ARMOR, INCREASED, 80.0..120.0),
                    effect(STOCK_EVASION, INCREASED, 80.0..120.0),
                ),
            )
        ),

        // ---------- BELT ----------
        UniqueTemplate(
            code = "MEGINORD_S_GIRDLE", slot = BELT, itemLevel = 20,
            modifiers = listOf(
                line(effect(STOCK_STRENGTH, ADD, 25.0..30.0)),
                line(effect(STOCK_ATTACK_PHYSICAL, ADD, 5.0..7.0)),
                line(effect(STOCK_RESIST_COLD, ADD, 20.0..30.0)),
                line(effect(STOCK_HEALTH_REGEN, ADD, 8.0..12.0)),
            )
        ),
        UniqueTemplate(
            code = "HEADHUNTER", slot = BELT, itemLevel = 40,
            modifiers = listOf(
                line(effect(STOCK_STRENGTH, ADD, 40.0..55.0),
                    effect(STOCK_AGILITY, ADD, 40.0..55.0),
                ),
                line(effect(STOCK_HEALTH, ADD, 50.0..70.0)),
            )
        ),
        UniqueTemplate(
            code = "THE_MAGNATE", slot = BELT, itemLevel = 20,
            modifiers = listOf(
                line(effect(STOCK_STRENGTH, ADD, 25.0..40.0)),
                line(effect(STOCK_RESIST_FIRE, ADD, 20.0..30.0)),
                line(effect(STOCK_RARITY, INCREASED, 10.0..20.0)),
            )
        ),

        // ---------- RING ----------
        UniqueTemplate(
            code = "BEREK_S_GRIP", slot = RING, itemLevel = 20,
            modifiers = listOf(
                line(effect(STOCK_HEALTH, ADD, 20.0..30.0)),
                line(effect(STOCK_RESIST_COLD, ADD, 20.0..30.0),
                    effect(STOCK_RESIST_LIGHTNING, ADD, 20.0..30.0),
                ),
                line(effect(STOCK_LEECH_PHYSICAL, ADD, 0.4..0.8)),
            )
        ),
        UniqueTemplate(
            code = "VENTOR_S_GAMBLE", slot = RING, itemLevel = 65,
            modifiers = listOf(
                line(effect(STOCK_RARITY, INCREASED, 20.0..50.0)),
                line(effect(STOCK_QUANTITY, INCREASED, 10.0..20.0)),
                line(effect(STOCK_HEALTH, ADD, 10.0..20.0)),
                line(effect(STOCK_RESIST_ALL, ADD, 10.0..30.0)),
            )
        ),
        UniqueTemplate(
            code = "KAOM_S_SIGN", slot = RING, itemLevel = 40,
            modifiers = listOf(
                line(effect(STOCK_STRENGTH, ADD, 15.0..25.0)),
                line(effect(STOCK_RESIST_FIRE, ADD, 20.0..30.0)),
                line(effect(STOCK_HEALTH_REGEN, ADD, 5.0..10.0)),
            )
        ),

        // ---------- AMULET ----------
        UniqueTemplate(
            code = "ASTRAMENTIS", slot = AMULET, itemLevel = 30,
            modifiers = listOf(
                line(effect(STOCK_STRENGTH, ADD, 80.0..100.0),
                    effect(STOCK_AGILITY, ADD, 80.0..100.0),
                    effect(STOCK_INTELLECT, ADD, 80.0..100.0),
                ),
            )
        ),
        UniqueTemplate(
            code = "CARNAGE_HEART", slot = AMULET, itemLevel = 40,
            modifiers = listOf(
                line(effect(STOCK_STRENGTH, ADD, 20.0..30.0),
                    effect(STOCK_AGILITY, ADD, 20.0..30.0),
                    effect(STOCK_INTELLECT, ADD, 20.0..30.0),
                ),
                line(effect(STOCK_LEECH_PHYSICAL, ADD, 0.6..1.0)),
                line(effect(STOCK_RESIST_ALL, ADD, 8.0..14.0)),
            )
        ),
        UniqueTemplate(
            code = "BISCO_S_COLLAR", slot = AMULET, itemLevel = 50,
            modifiers = listOf(
                line(effect(STOCK_RARITY, INCREASED, 50.0..100.0)),
                line(effect(STOCK_QUANTITY, INCREASED, 20.0..30.0)),
            )
        ),

        // ---------- SHIELD ----------
        UniqueTemplate(
            code = "LIONEYE_S_REMORSE", slot = SHIELD, itemLevel = 45, defense = 800,
            modifiers = listOf(
                line(effect(STOCK_HEALTH, ADD, 100.0..120.0)),
                line(effect(STOCK_ARMOR, INCREASED, 200.0..250.0)),
                line(effect(STOCK_BLOCK_CHANCE, ADD, 5.0..8.0)),
            )
        ),
        UniqueTemplate(
            code = "RISE_OF_THE_PHOENIX", slot = SHIELD, itemLevel = 60, defense = 400,
            modifiers = listOf(
                line(effect(STOCK_HEALTH, ADD, 60.0..80.0)),
                line(effect(STOCK_RESIST_FIRE, ADD, 40.0..50.0)),
                line(effect(STOCK_ARMOR, INCREASED, 150.0..200.0)),
                line(effect(STOCK_HEALTH_REGEN, ADD, 10.0..20.0)),
            )
        ),
        UniqueTemplate(
            code = "SAFFELL_S_FRAME", slot = SHIELD, itemLevel = 45, defense = 120,
            modifiers = listOf(
                line(effect(STOCK_RESIST_ALL, ADD, 20.0..25.0)),
                line(effect(STOCK_ATTACK_MAGICAL, INCREASED, 20.0..40.0)),
                line(effect(STOCK_BLOCK_CHANCE, ADD, 5.0..8.0)),
            )
        ),

        // ---------- QUIVER ----------
        UniqueTemplate(
            code = "DRILLNECK", slot = QUIVER, itemLevel = 40,
            modifiers = listOf(
                line(effect(STOCK_AGILITY, ADD, 20.0..30.0)),
                line(effect(STOCK_ATTACK_SPEED, INCREASED, 8.0..12.0)),
                line(effect(STOCK_ATTACK_PHYSICAL, INCREASED, 20.0..40.0)),
            )
        ),
        UniqueTemplate(
            code = "REARGUARD", slot = QUIVER, itemLevel = 30,
            modifiers = listOf(
                line(effect(STOCK_ENERGY_SHIELD, ADD, 30.0..50.0)),
                line(effect(STOCK_HEALTH, ADD, 40.0..60.0)),
                line(effect(STOCK_BLOCK_CHANCE, ADD, 3.0..5.0)),
            )
        ),
        UniqueTemplate(
            code = "HYRRI_S_BITE", slot = QUIVER, itemLevel = 45,
            modifiers = listOf(
                line(effect(STOCK_AGILITY, ADD, 20.0..30.0)),
                line(effect(STOCK_ATTACK_COLD, ADD, 15.0..25.0)),
                line(effect(STOCK_LEECH_PHYSICAL, ADD, 0.4..0.8)),
            )
        ),

        // ---------- WEAPON_1H ----------
        UniqueTemplate(
            code = "BINO_S_KITCHEN_KNIFE", slot = WEAPON_1H, itemLevel = 50,
            weaponType = BLADE, damageMin = 40.0, damageMax = 90.0, attackSpeed = 1.4, durability = 120,
            modifiers = listOf(
                line(effect(STOCK_CRITICAL_CHANCE, INCREASED, 30.0..40.0)),
                line(effect(STOCK_HEALTH, ADD, 20.0..30.0)),
                line(effect(STOCK_ATTACK_PHYSICAL, INCREASED, 50.0..70.0)),
            )
        ),
        UniqueTemplate(
            code = "DORYANI_S_CATALYST", slot = WEAPON_1H, itemLevel = 60,
            weaponType = WAND, damageMin = 30.0, damageMax = 70.0, attackSpeed = 1.4, durability = 100,
            modifiers = listOf(
                line(effect(STOCK_ATTACK_LIGHTNING, ADD, 10.0..80.0)),
                line(effect(STOCK_ATTACK_MAGICAL, INCREASED, 40.0..60.0)),
                line(effect(STOCK_LEECH_PHYSICAL, ADD, 0.4..0.8)),
            )
        ),
        UniqueTemplate(
            code = "LIONEYE_S_GLARE", slot = WEAPON_1H, itemLevel = 70,
            weaponType = BOW, damageMin = 120.0, damageMax = 250.0, attackSpeed = 1.4, durability = 140,
            modifiers = listOf(
                line(effect(STOCK_ATTACK_PHYSICAL, INCREASED, 150.0..200.0)),
                line(effect(STOCK_ATTACK_SPEED, INCREASED, 10.0..14.0)),
                line(effect(STOCK_AGILITY, ADD, 30.0..40.0)),
            )
        ),

        // ---------- WEAPON_2H ----------
        UniqueTemplate(
            code = "MAROHI_ERQI", slot = WEAPON_2H, itemLevel = 50,
            weaponType = DOUBLEAXE, damageMin = 150.0, damageMax = 400.0, attackSpeed = 0.8, durability = 200,
            modifiers = listOf(
                line(effect(STOCK_ATTACK_PHYSICAL, INCREASED, 250.0..300.0)),
                line(effect(STOCK_STRENGTH, ADD, 30.0..40.0)),
            )
        ),
        UniqueTemplate(
            code = "STARFORGE", slot = WEAPON_2H, itemLevel = 75,
            weaponType = DOUBLESWORD, damageMin = 200.0, damageMax = 400.0, attackSpeed = 1.3, durability = 220,
            modifiers = listOf(
                line(effect(STOCK_ATTACK_PHYSICAL, INCREASED, 300.0..400.0)),
                line(effect(STOCK_STRENGTH, ADD, 40.0..50.0)),
                line(effect(STOCK_ATTACK_PHYSICAL, MORE, 10.0..15.0)),
            )
        ),
        UniqueTemplate(
            code = "HEGEMONY_S_ERA", slot = WEAPON_2H, itemLevel = 65,
            weaponType = LONGSWORD, damageMin = 140.0, damageMax = 300.0, attackSpeed = 1.2, durability = 180,
            modifiers = listOf(
                line(effect(STOCK_STRENGTH, ADD, 20.0..30.0),
                    effect(STOCK_AGILITY, ADD, 20.0..30.0),
                ),
                line(effect(STOCK_ATTACK_PHYSICAL, INCREASED, 150.0..200.0)),
                line(effect(STOCK_CRITICAL_MULTIPLIER, ADD, 20.0..30.0)),
            )
        ),
    )

    // ==================== Генерация документов ====================

    private fun UniqueTemplate.modifierTemplates(): List<ModifierTemplate> =
        modifiers.mapIndexed { index, modifier ->
            ModifierTemplate(
                code = modifierCode(code, index),
                source = EnumModifierSource.UNIQUE,
                effects = modifier.effects,
                // У уникалки один тир: диапазон фиксирован самим предметом
                tierCount = 1,
                bestItemLevel = itemLevel,
                tags = listOf("unique", slot.name.lowercase())
            )
        }

    private fun modifierCode(itemCode: String, index: Int): String = "UNIQUE_${itemCode}_$index"

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
            .mapTo(mutableListOf()) { resolve(modifierCode(template.code, it)) }

        template.toEquipment(modifierIds, resolve)
    }

    /**
     * База предмета фиксированными модификаторами: броня, урон, скорость атаки.
     */
    private fun UniqueTemplate.baseParams(resolve: (String) -> String): MutableList<Modifier> {
        val result = mutableListOf<Modifier>()

        if (defense > 0) result.add(Modifier.passive(resolve("IMPLICIT_ARMOUR_BASE"), listOf(defense.toDouble())))
        if (damageMax > 0.0) {
            val average = ((damageMin + damageMax) / 2.0).to1Digits()
            result.add(Modifier.passive(resolve("IMPLICIT_PHYSICAL_DAMAGE_BASE"), listOf(average)))
            result.add(Modifier.passive(resolve("IMPLICIT_ATTACK_SPEED_BASE"), listOf(attackSpeed)))
        }

        return result
    }

    /**
     * Требования уникалки выводятся из её уровня: слот решает, какой атрибут нужен.
     */
    private fun UniqueTemplate.requirement(vararg attributeSlots: EnumEquipmentType): Int =
        if (slot in attributeSlots) itemLevel * 2 else 0

    private fun UniqueTemplate.toEquipment(
        modifierIds: MutableList<String>,
        resolve: (String) -> String
    ): Equipment {
        val base = baseParams(resolve)
        val strength = requirement(HELMET, BODY, SHIELD, BELT, WEAPON_2H)
        val dexterity = requirement(BOOTS, GLOVES, QUIVER, WEAPON_1H)
        val intelligence = requirement(RING, AMULET, WINGS)

        return when (slot) {
            WEAPON_1H, WEAPON_2H -> Weapon(
                slot = slot,
                weaponType = weaponType ?: BLADE,
                durability = durability,
                code = code,
                rarity = EnumRarity.UNIQUE,
                itemLevel = itemLevel,
                modifierIds = modifierIds,
                baseParams = base,
                requiredLevel = itemLevel,
                requiredStrength = strength,
                requiredDexterity = dexterity,
                requiredIntelligence = intelligence
            )

            RING, AMULET, BELT, QUIVER -> Accessory(
                slot = slot,
                code = code,
                rarity = EnumRarity.UNIQUE,
                itemLevel = itemLevel,
                modifierIds = modifierIds,
                baseParams = base,
                requiredLevel = itemLevel,
                requiredStrength = strength,
                requiredDexterity = dexterity,
                requiredIntelligence = intelligence
            )

            else -> Armor(
                slot = slot,
                code = code,
                rarity = EnumRarity.UNIQUE,
                itemLevel = itemLevel,
                modifierIds = modifierIds,
                baseParams = base,
                requiredLevel = itemLevel,
                requiredStrength = strength,
                requiredDexterity = dexterity,
                requiredIntelligence = intelligence
            )
        }
    }
}

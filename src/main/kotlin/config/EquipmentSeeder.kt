package config

import application.enums.EnumEquipmentType
import application.enums.EnumModifierSource
import application.enums.EnumRarity
import base.exception.model.EquipmentExceptions
import base.exception.model.ModifierExceptions
import features.data.equipment.equipment_data.Equipment
import features.data.equipment.equipment_data.Armor
import extensions.toStableObjectId
import features.logic.modifiers.Modifier
import features.logic.modifiers.ModifierDefinition

/**
 * Начальные данные коллекции `Equipment`.
 *
 * Шаблоны ссылаются на модификаторы по их _id, поэтому сидер создаётся
 * уже с сохранёнными описаниями модификаторов.
 *
 * Пул модификаторов, как в POE, задаётся классом предмета, а не конкретной
 * базой: любой шлем может выролить любой шлемный модификатор.
 * Уникальные предметы живут в [UniqueEquipmentSeeder]: у них пула нет,
 * вместо него закреплённый набор модификаторов.
 *
 * @param definitions документы коллекции `ModifierDefinition`
 */
class EquipmentSeeder(definitions: List<ModifierDefinition>) {

    private val byCode: Map<String, ModifierDefinition> = definitions.associateBy { it.code }

    /**
     * Описания, которые роллятся случайно и занимают префикс или суффикс предмета.
     */
    private val rollable = definitions.filter {
        it.source == EnumModifierSource.PREFIX || it.source == EnumModifierSource.SUFFIX
    }

    /**
     * Ссылка на описание модификатора по его коду.
     */
    private fun mod(code: String): String =
        byCode[code]?._id ?: throw ModifierExceptions.funExceptionCodeNotFound("mod", code)

    /**
     * Пул роллящихся модификаторов, у которых есть хотя бы один из тегов.
     */
    private fun pool(vararg anyTags: String): MutableList<String> =
        rollable.filter { definition -> definition.tags?.any { it in anyTags } == true }
            .mapTo(mutableListOf()) { it._id }

    /**
     * Модификаторы, доступные шлемам: запас характеристик, защита,
     * сопротивления, атрибуты, регенерация и редкость добычи.
     *
     * @param extraCodes коды модификаторов, которые вешаются на базу сверх пула
     */
    private fun helmetModifiers(vararg extraCodes: String): MutableList<String> =
        pool("life", "mana", "energy_shield", "armour", "evasion", "resistance", "attribute", "regen", "rarity")
            .apply { extraCodes.forEach { add(mod(it)) } }

    /**
     * База шлема: фиксированная броня implicit-модификатором.
     */
    private fun helmetBase(armour: Double): MutableList<Modifier> =
        mutableListOf(Modifier.passive(mod("IMPLICIT_ARMOUR_BASE"), listOf(armour)))

    fun seed(): ArrayList<Equipment> {
        val list = ArrayList<Equipment>()

        seedHelmets(list)
        list.addAll(UniqueEquipmentSeeder.seedEquipment(::mod))

        // Имя - натуральный ключ шаблона, дубликаты сломали бы стабильный _id
        val duplicates = list.groupBy { it.name }.filterValues { it.size > 1 }.keys
        if (duplicates.isNotEmpty())
            throw EquipmentExceptions.funException("seed", "Duplicate equipment names: $duplicates")

        // Шаблоны пересеваются на каждом старте, поэтому _id должен быть
        // стабильным: иначе инвентарь персонажей потеряет ссылки на них.
        list.forEach { it._id = it.name.toStableObjectId() }

        return list
    }

    private fun seedHelmets(list: ArrayList<Equipment>) {
        // ==================== COMMON HELMETS ====================
        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(12.0),
                name = "Leather Cap",
                rarity = EnumRarity.COMMON,
                itemLevel = 1,
                requiredLevel = 1,
                requiredStrength = 2,
                modifierIds = helmetModifiers()
            ).apply { this.description = "A simple leather headguard." })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(15.0),
                name = "Iron Skullcap",
                rarity = EnumRarity.COMMON,
                itemLevel = 5,
                requiredLevel = 5,
                requiredStrength = 10,
                modifierIds = helmetModifiers()
            ).apply { this.description = "Light iron cap for basic protection." })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(18.0),
                name = "Hide Helm",
                rarity = EnumRarity.COMMON,
                itemLevel = 10,
                requiredLevel = 10,
                requiredStrength = 20,
                modifierIds = helmetModifiers()
            ).apply { this.description = "Reinforced hide headgear." })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(20.0),
                name = "Chain Coif",
                rarity = EnumRarity.COMMON,
                itemLevel = 15,
                requiredLevel = 15,
                requiredStrength = 30,
                modifierIds = helmetModifiers()
            ).apply { this.description = "Basic chainmail hood." })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(22.0),
                name = "Sallet",
                rarity = EnumRarity.COMMON,
                itemLevel = 20,
                requiredLevel = 20,
                requiredStrength = 40,
                modifierIds = helmetModifiers()
            ).apply { this.description = "Standard military helmet." })

        // ==================== UNCOMMON HELMETS ====================
        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(28.0),
                name = "Steel Helm",
                rarity = EnumRarity.UNCOMMON,
                itemLevel = 25,
                requiredLevel = 25,
                requiredStrength = 50,
                modifierIds = helmetModifiers()
            ).apply { this.description = "Sturdy steel helmet." })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(32.0),
                name = "Knight's Casque",
                rarity = EnumRarity.UNCOMMON,
                itemLevel = 30,
                requiredLevel = 30,
                requiredStrength = 60,
                modifierIds = helmetModifiers()
            ).apply { this.description = "Full-face knight helmet." })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(36.0),
                name = "Bronze Greathelm",
                rarity = EnumRarity.UNCOMMON,
                itemLevel = 35,
                requiredLevel = 35,
                requiredStrength = 70,
                modifierIds = helmetModifiers()
            ).apply { this.description = "Heavy bronze great helm." })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(40.0),
                name = "Visored Helm",
                rarity = EnumRarity.UNCOMMON,
                itemLevel = 40,
                requiredLevel = 40,
                requiredStrength = 80,
                modifierIds = helmetModifiers()
            ).apply { this.description = "Helm with protective visor." })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(44.0),
                name = "Warden's Crown",
                rarity = EnumRarity.UNCOMMON,
                itemLevel = 45,
                requiredLevel = 45,
                requiredStrength = 90,
                modifierIds = helmetModifiers()
            ).apply { this.description = "Guardian's protective crown." })

        // ==================== RARE HELMETS ====================
        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(52.0),
                name = "Helm of Valor",
                rarity = EnumRarity.RARE,
                itemLevel = 50,
                requiredLevel = 50,
                requiredStrength = 100,
                modifierIds = helmetModifiers()
            ).apply { this.description = "Forged for brave warriors." })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(58.0),
                name = "Battle Mask",
                rarity = EnumRarity.RARE,
                itemLevel = 55,
                requiredLevel = 55,
                requiredStrength = 110,
                modifierIds = helmetModifiers()
            ).apply { this.description = "Intimidating battle mask." })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(64.0),
                name = "Aegis Helm",
                rarity = EnumRarity.RARE,
                itemLevel = 60,
                requiredLevel = 60,
                requiredStrength = 120,
                modifierIds = helmetModifiers()
            ).apply { this.description = "Shield-protected helm." })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(70.0),
                name = "Fury's Visage",
                rarity = EnumRarity.RARE,
                itemLevel = 65,
                requiredLevel = 65,
                requiredStrength = 130,
                modifierIds = helmetModifiers()
            ).apply { this.description = "Mask of the raging warrior." })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(76.0),
                name = "Warrior's Sallet",
                rarity = EnumRarity.RARE,
                itemLevel = 70,
                requiredLevel = 70,
                requiredStrength = 140,
                modifierIds = helmetModifiers()
            ).apply { this.description = "Elite warrior's sallet." })

        // ==================== EPIC HELMETS ====================
        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(88.0),
                name = "Helm of Justice",
                rarity = EnumRarity.EPIC,
                itemLevel = 75,
                requiredLevel = 75,
                requiredStrength = 150,
                modifierIds = helmetModifiers()
            ).apply { this.description = "Blessed helm of righteous warriors." })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(96.0),
                name = "Crown of Glory",
                rarity = EnumRarity.EPIC,
                itemLevel = 80,
                requiredLevel = 80,
                requiredStrength = 160,
                modifierIds = helmetModifiers()
            ).apply { this.description = "Crown worn by legendary champions." })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(104.0),
                name = "Helm of the Martyr",
                rarity = EnumRarity.EPIC,
                itemLevel = 85,
                requiredLevel = 85,
                requiredStrength = 170,
                modifierIds = helmetModifiers()
            ).apply { this.description = "Forged in sacrifice and pain." })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(112.0),
                name = "Radiant Casque",
                rarity = EnumRarity.EPIC,
                itemLevel = 90,
                requiredLevel = 90,
                requiredStrength = 180,
                modifierIds = helmetModifiers()
            ).apply { this.description = "Shining with inner light." })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(120.0),
                name = "Helm of Enlightenment",
                rarity = EnumRarity.EPIC,
                itemLevel = 95,
                requiredLevel = 95,
                requiredStrength = 190,
                modifierIds = helmetModifiers()
            ).apply { this.description = "Grants clarity of mind and body." })

        // ==================== MYTHICAL HELMETS ====================
        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(138.0),
                name = "Azure Crown",
                rarity = EnumRarity.MYTHICAL,
                itemLevel = 4,
                requiredLevel = 4,
                requiredStrength = 8,
                modifierIds = helmetModifiers(),
            ).apply {
            this.description = "Crown of the azure kings."
        })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(150.0),
                name = "Helm of the Titan",
                rarity = EnumRarity.MYTHICAL,
                itemLevel = 16,
                requiredLevel = 16,
                requiredStrength = 32,
                modifierIds = helmetModifiers(),
            ).apply {
            this.description = "Forged in the heart of a mountain."
        })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(162.0),
                name = "Dragonlord's Helm",
                rarity = EnumRarity.MYTHICAL,
                itemLevel = 72,
                requiredLevel = 72,
                requiredStrength = 144,
                modifierIds = helmetModifiers(),
            ).apply {
            this.description = "Worn by dragon masters."
        })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(174.0),
                name = "Helm of Immortality",
                rarity = EnumRarity.MYTHICAL,
                itemLevel = 44,
                requiredLevel = 44,
                requiredStrength = 88,
                modifierIds = helmetModifiers("IMPLICIT_ADD_ARMOUR"),
            ).apply {
            this.description = "Grants eternal vitality."
        })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(186.0),
                name = "Legendary Casque",
                rarity = EnumRarity.MYTHICAL,
                itemLevel = 90,
                requiredLevel = 90,
                requiredStrength = 180,
                modifierIds = helmetModifiers(),
            ).apply {
            this.description = "The ultimate head protection."
        })
    }
}
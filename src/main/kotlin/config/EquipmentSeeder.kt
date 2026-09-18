package config

import application.enums.EnumEquipmentType
import application.enums.EnumModifierSource
import application.enums.EnumRarity
import base.exception.model.ModifierExceptions
import features.data.equipment.equipment_data.Equipment
import features.data.equipment.equipment_data.Armor
import features.logic.modifiers.ModifierDefinition

/**
 * Начальные данные коллекции `Equipment`.
 *
 * Шаблоны ссылаются на модификаторы по их _id, поэтому сидер создаётся
 * уже с сохранёнными описаниями модификаторов.
 *
 * Пул модификаторов, как в POE, задаётся классом предмета, а не конкретной
 * базой: любой шлем может выролить любой шлемный модификатор.
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

    fun seed(): ArrayList<Equipment> {
        val list = ArrayList<Equipment>()

        seedHelmets(list)

        return list
    }

    private fun seedHelmets(list: ArrayList<Equipment>) {
        // ==================== COMMON HELMETS ====================
        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                defense = 12,
                name = "Leather Cap",
                rarity = EnumRarity.COMMON,
                itemLevel = 1,
                modifierIds = helmetModifiers()
            ).apply { this.description = "A simple leather headguard." })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                defense = 15,
                name = "Iron Skullcap",
                rarity = EnumRarity.COMMON,
                itemLevel = 5,
                modifierIds = helmetModifiers()
            ).apply { this.description = "Light iron cap for basic protection." })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                defense = 18,
                name = "Hide Helm",
                rarity = EnumRarity.COMMON,
                itemLevel = 10,
                modifierIds = helmetModifiers()
            ).apply { this.description = "Reinforced hide headgear." })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                defense = 20,
                name = "Chain Coif",
                rarity = EnumRarity.COMMON,
                itemLevel = 15,
                modifierIds = helmetModifiers()
            ).apply { this.description = "Basic chainmail hood." })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                defense = 22,
                name = "Sallet",
                rarity = EnumRarity.COMMON,
                itemLevel = 20,
                modifierIds = helmetModifiers()
            ).apply { this.description = "Standard military helmet." })

        // ==================== UNCOMMON HELMETS ====================
        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                defense = 28,
                name = "Steel Helm",
                rarity = EnumRarity.UNCOMMON,
                itemLevel = 25,
                modifierIds = helmetModifiers()
            ).apply { this.description = "Sturdy steel helmet." })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                defense = 32,
                name = "Knight's Casque",
                rarity = EnumRarity.UNCOMMON,
                itemLevel = 30,
                modifierIds = helmetModifiers()
            ).apply { this.description = "Full-face knight helmet." })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                defense = 36,
                name = "Bronze Greathelm",
                rarity = EnumRarity.UNCOMMON,
                itemLevel = 35,
                modifierIds = helmetModifiers()
            ).apply { this.description = "Heavy bronze great helm." })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                defense = 40,
                name = "Visored Helm",
                rarity = EnumRarity.UNCOMMON,
                itemLevel = 40,
                modifierIds = helmetModifiers()
            ).apply { this.description = "Helm with protective visor." })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                defense = 44,
                name = "Warden's Crown",
                rarity = EnumRarity.UNCOMMON,
                itemLevel = 45,
                modifierIds = helmetModifiers()
            ).apply { this.description = "Guardian's protective crown." })

        // ==================== RARE HELMETS ====================
        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                defense = 52,
                name = "Helm of Valor",
                rarity = EnumRarity.RARE,
                itemLevel = 50,
                modifierIds = helmetModifiers()
            ).apply { this.description = "Forged for brave warriors." })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                defense = 58,
                name = "Battle Mask",
                rarity = EnumRarity.RARE,
                itemLevel = 55,
                modifierIds = helmetModifiers()
            ).apply { this.description = "Intimidating battle mask." })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                defense = 64,
                name = "Aegis Helm",
                rarity = EnumRarity.RARE,
                itemLevel = 60,
                modifierIds = helmetModifiers()
            ).apply { this.description = "Shield-protected helm." })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                defense = 70,
                name = "Fury's Visage",
                rarity = EnumRarity.RARE,
                itemLevel = 65,
                modifierIds = helmetModifiers()
            ).apply { this.description = "Mask of the raging warrior." })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                defense = 76,
                name = "Warrior's Sallet",
                rarity = EnumRarity.RARE,
                itemLevel = 70,
                modifierIds = helmetModifiers()
            ).apply { this.description = "Elite warrior's sallet." })

        // ==================== EPIC HELMETS ====================
        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                defense = 88,
                name = "Helm of Justice",
                rarity = EnumRarity.EPIC,
                itemLevel = 75,
                modifierIds = helmetModifiers()
            ).apply { this.description = "Blessed helm of righteous warriors." })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                defense = 96,
                name = "Crown of Glory",
                rarity = EnumRarity.EPIC,
                itemLevel = 80,
                modifierIds = helmetModifiers()
            ).apply { this.description = "Crown worn by legendary champions." })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                defense = 104,
                name = "Helm of the Martyr",
                rarity = EnumRarity.EPIC,
                itemLevel = 85,
                modifierIds = helmetModifiers()
            ).apply { this.description = "Forged in sacrifice and pain." })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                defense = 112,
                name = "Radiant Casque",
                rarity = EnumRarity.EPIC,
                itemLevel = 90,
                modifierIds = helmetModifiers()
            ).apply { this.description = "Shining with inner light." })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                defense = 120,
                name = "Helm of Enlightenment",
                rarity = EnumRarity.EPIC,
                itemLevel = 95,
                modifierIds = helmetModifiers()
            ).apply { this.description = "Grants clarity of mind and body." })

        // ==================== LEGENDARY HELMETS ====================
        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                defense = 138,
                name = "Azure Crown",
                rarity = EnumRarity.LEGENDARY,
                itemLevel = 4,
                modifierIds = helmetModifiers(),
            ).apply {
            this.description = "Crown of the azure kings."
        })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                defense = 150,
                name = "Helm of the Titan",
                rarity = EnumRarity.LEGENDARY,
                itemLevel = 16,
                modifierIds = helmetModifiers(),
            ).apply {
            this.description = "Forged in the heart of a mountain."
        })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                defense = 162,
                name = "Dragonlord's Helm",
                rarity = EnumRarity.LEGENDARY,
                itemLevel = 72,
                modifierIds = helmetModifiers(),
            ).apply {
            this.description = "Worn by dragon masters."
        })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                defense = 174,
                name = "Helm of Immortality",
                rarity = EnumRarity.LEGENDARY,
                itemLevel = 44,
                modifierIds = helmetModifiers("IMPLICIT_ADD_ARMOUR"),
            ).apply {
            this.description = "Grants eternal vitality."
        })

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                defense = 186,
                name = "Legendary Casque",
                rarity = EnumRarity.LEGENDARY,
                itemLevel = 90,
                modifierIds = helmetModifiers(),
            ).apply {
            this.description = "The ultimate head protection."
        })
    }
}
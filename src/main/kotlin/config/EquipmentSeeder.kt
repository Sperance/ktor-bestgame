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

        // Код - натуральный ключ шаблона, дубликаты сломали бы стабильный _id
        // и склеили бы двум предметам один текст локализации
        val duplicates = list.groupBy { it.code }.filterValues { it.size > 1 }.keys
        if (duplicates.isNotEmpty())
            throw EquipmentExceptions.funException("seed", "Duplicate equipment codes: $duplicates")

        // Шаблоны пересеваются на каждом старте, поэтому _id должен быть
        // стабильным: иначе инвентарь персонажей потеряет ссылки на них.
        list.forEach { it._id = it.code.toStableObjectId() }

        return list
    }

    private fun seedHelmets(list: ArrayList<Equipment>) {
        // ==================== COMMON HELMETS ====================
        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(12.0),
                code = "LEATHER_CAP",
                rarity = EnumRarity.COMMON,
                itemLevel = 1,
                requiredLevel = 1,
                requiredStrength = 2,
                modifierIds = helmetModifiers()
            ))

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(15.0),
                code = "IRON_SKULLCAP",
                rarity = EnumRarity.COMMON,
                itemLevel = 5,
                requiredLevel = 5,
                requiredStrength = 10,
                modifierIds = helmetModifiers()
            ))

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(18.0),
                code = "HIDE_HELM",
                rarity = EnumRarity.COMMON,
                itemLevel = 10,
                requiredLevel = 10,
                requiredStrength = 20,
                modifierIds = helmetModifiers()
            ))

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(20.0),
                code = "CHAIN_COIF",
                rarity = EnumRarity.COMMON,
                itemLevel = 15,
                requiredLevel = 15,
                requiredStrength = 30,
                modifierIds = helmetModifiers()
            ))

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(22.0),
                code = "SALLET",
                rarity = EnumRarity.COMMON,
                itemLevel = 20,
                requiredLevel = 20,
                requiredStrength = 40,
                modifierIds = helmetModifiers()
            ))

        // ==================== UNCOMMON HELMETS ====================
        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(28.0),
                code = "STEEL_HELM",
                rarity = EnumRarity.UNCOMMON,
                itemLevel = 25,
                requiredLevel = 25,
                requiredStrength = 50,
                modifierIds = helmetModifiers()
            ))

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(32.0),
                code = "KNIGHT_S_CASQUE",
                rarity = EnumRarity.UNCOMMON,
                itemLevel = 30,
                requiredLevel = 30,
                requiredStrength = 60,
                modifierIds = helmetModifiers()
            ))

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(36.0),
                code = "BRONZE_GREATHELM",
                rarity = EnumRarity.UNCOMMON,
                itemLevel = 35,
                requiredLevel = 35,
                requiredStrength = 70,
                modifierIds = helmetModifiers()
            ))

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(40.0),
                code = "VISORED_HELM",
                rarity = EnumRarity.UNCOMMON,
                itemLevel = 40,
                requiredLevel = 40,
                requiredStrength = 80,
                modifierIds = helmetModifiers()
            ))

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(44.0),
                code = "WARDEN_S_CROWN",
                rarity = EnumRarity.UNCOMMON,
                itemLevel = 45,
                requiredLevel = 45,
                requiredStrength = 90,
                modifierIds = helmetModifiers()
            ))

        // ==================== RARE HELMETS ====================
        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(52.0),
                code = "HELM_OF_VALOR",
                rarity = EnumRarity.RARE,
                itemLevel = 50,
                requiredLevel = 50,
                requiredStrength = 100,
                modifierIds = helmetModifiers()
            ))

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(58.0),
                code = "BATTLE_MASK",
                rarity = EnumRarity.RARE,
                itemLevel = 55,
                requiredLevel = 55,
                requiredStrength = 110,
                modifierIds = helmetModifiers()
            ))

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(64.0),
                code = "AEGIS_HELM",
                rarity = EnumRarity.RARE,
                itemLevel = 60,
                requiredLevel = 60,
                requiredStrength = 120,
                modifierIds = helmetModifiers()
            ))

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(70.0),
                code = "FURY_S_VISAGE",
                rarity = EnumRarity.RARE,
                itemLevel = 65,
                requiredLevel = 65,
                requiredStrength = 130,
                modifierIds = helmetModifiers()
            ))

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(76.0),
                code = "WARRIOR_S_SALLET",
                rarity = EnumRarity.RARE,
                itemLevel = 70,
                requiredLevel = 70,
                requiredStrength = 140,
                modifierIds = helmetModifiers()
            ))

        // ==================== EPIC HELMETS ====================
        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(88.0),
                code = "HELM_OF_JUSTICE",
                rarity = EnumRarity.EPIC,
                itemLevel = 75,
                requiredLevel = 75,
                requiredStrength = 150,
                modifierIds = helmetModifiers()
            ))

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(96.0),
                code = "CROWN_OF_GLORY",
                rarity = EnumRarity.EPIC,
                itemLevel = 80,
                requiredLevel = 80,
                requiredStrength = 160,
                modifierIds = helmetModifiers()
            ))

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(104.0),
                code = "HELM_OF_THE_MARTYR",
                rarity = EnumRarity.EPIC,
                itemLevel = 85,
                requiredLevel = 85,
                requiredStrength = 170,
                modifierIds = helmetModifiers()
            ))

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(112.0),
                code = "RADIANT_CASQUE",
                rarity = EnumRarity.EPIC,
                itemLevel = 90,
                requiredLevel = 90,
                requiredStrength = 180,
                modifierIds = helmetModifiers()
            ))

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(120.0),
                code = "HELM_OF_ENLIGHTENMENT",
                rarity = EnumRarity.EPIC,
                itemLevel = 95,
                requiredLevel = 95,
                requiredStrength = 190,
                modifierIds = helmetModifiers()
            ))

        // ==================== MYTHICAL HELMETS ====================
        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(138.0),
                code = "AZURE_CROWN",
                rarity = EnumRarity.MYTHICAL,
                itemLevel = 4,
                requiredLevel = 4,
                requiredStrength = 8,
                modifierIds = helmetModifiers(),
            ))

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(150.0),
                code = "HELM_OF_THE_TITAN",
                rarity = EnumRarity.MYTHICAL,
                itemLevel = 16,
                requiredLevel = 16,
                requiredStrength = 32,
                modifierIds = helmetModifiers(),
            ))

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(162.0),
                code = "DRAGONLORD_S_HELM",
                rarity = EnumRarity.MYTHICAL,
                itemLevel = 72,
                requiredLevel = 72,
                requiredStrength = 144,
                modifierIds = helmetModifiers(),
            ))

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(174.0),
                code = "HELM_OF_IMMORTALITY",
                rarity = EnumRarity.MYTHICAL,
                itemLevel = 44,
                requiredLevel = 44,
                requiredStrength = 88,
                modifierIds = helmetModifiers("IMPLICIT_ADD_ARMOUR"),
            ))

        list.add(
            Armor(
                slot = EnumEquipmentType.HELMET,
                baseParams = helmetBase(186.0),
                code = "LEGENDARY_CASQUE",
                rarity = EnumRarity.MYTHICAL,
                itemLevel = 90,
                requiredLevel = 90,
                requiredStrength = 180,
                modifierIds = helmetModifiers(),
            ))
    }
}
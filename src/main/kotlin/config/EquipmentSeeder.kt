package config

import application.enums.EnumEquipmentType
import application.enums.EnumEquipmentWeapon
import application.enums.EnumRarity
import base.exception.model.EquipmentExceptions
import base.exception.model.ModifierExceptions
import extensions.toStableObjectId
import features.data.equipment.equipment_data.Accessory
import features.data.equipment.equipment_data.Armor
import features.data.equipment.equipment_data.Equipment
import features.data.equipment.equipment_data.Weapon
import features.logic.modifiers.Modifier
import features.logic.modifiers.ModifierDefinition
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Начальные данные коллекции `Equipment`: обычные шаблоны из `resources/content/equipment.json`
 * и уникалки из `uniques.json` ([UniqueEquipmentSeeder]) - запись у них одна.
 *
 * С 0.39.0 шаблон не хранит раскрытого пула: он называет пулы модификаторов, из которых роллит
 * (`modifierPools` - пул слота, как в POE, и локальные пулы своей базы: броневой шлем называет
 * `local:armor`, шлем уклонения - `local:evasion`). В каких пулах экипировки состоит сам шаблон
 * (`drop`, `smith`, `merchant`, пулы уникалок), с 0.56.0 говорит `pools.json`. Закреплённые
 * модификаторы - implicit и строки уникалки - лежат кодами в `fixedModifierCodes`.
 *
 * @param definitions документы коллекции `ModifierDefinition`: коды ссылок проверяются по ним
 */
class EquipmentSeeder(definitions: List<ModifierDefinition>) {

    companion object {
        /**
         * Файл с обычными шаблонами экипировки.
         */
        const val FILE = "equipment.json"
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val byCode: Map<String, ModifierDefinition> = definitions.associateBy { it.code }

    /**
     * Ссылка на описание модификатора - его код, если такое описание есть.
     */
    private fun mod(code: String): String =
        byCode[code]?.code ?: throw ModifierExceptions.funExceptionCodeNotFound("mod", code)

    fun seed(): ArrayList<Equipment> {
        val list = ArrayList<Equipment>()

        json.decodeFromString(EquipmentDocument.serializer(), ContentResource.read(FILE)).equipment.mapTo(list) { it.toEquipment() }
        UniqueEquipmentSeeder.records.mapTo(list) { it.toEquipment() }

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

    // ==================== Чтение содержимого ====================

    @Serializable
    data class BaseParam(val code: String, val values: List<Double> = emptyList())

    /**
     * Шаблон в том виде, в каком он лежит в файле. [lines] есть только у уникалки.
     */
    @Serializable
    data class EquipmentRecord(
        val type: String,
        val slot: EnumEquipmentType,
        val code: String,
        val rarity: EnumRarity = EnumRarity.COMMON,
        val itemLevel: Int = 1,
        val requiredLevel: Int = 1,
        val requiredStrength: Int = 0,
        val requiredDexterity: Int = 0,
        val requiredIntelligence: Int = 0,
        val weaponType: EnumEquipmentWeapon? = null,
        val durability: Int = 100,
        val baseParams: List<BaseParam> = emptyList(),
        val modifierPools: List<String> = emptyList(),
        val fixedModifiers: List<String> = emptyList(),
        val lines: List<List<UniqueEquipmentSeeder.UniqueEffect>> = emptyList(),
    )

    @Serializable
    private data class EquipmentDocument(val equipment: List<EquipmentRecord> = emptyList())

    private fun EquipmentRecord.toEquipment(): Equipment {
        if (modifierPools.any { it.isBlank() })
            throw EquipmentExceptions.funException("toEquipment", "Broken pools of $code")
        val base = baseParams.mapTo(mutableListOf()) { Modifier.passive(mod(it.code), it.values) }
        val fixed = (fixedModifiers + lines.indices.map { UniqueEquipmentSeeder.modifierCode(code, it) }).mapTo(mutableListOf(), ::mod)
        val affixPools = modifierPools.toMutableList()

        return when (type) {
            "weapon" -> Weapon(
                slot = slot, weaponType = weaponType ?: EnumEquipmentWeapon.BLADE, durability = durability,
                code = code, rarity = rarity, itemLevel = itemLevel, fixedModifierCodes = fixed, modifierPools = affixPools,
                baseParams = base, requiredLevel = requiredLevel, requiredStrength = requiredStrength,
                requiredDexterity = requiredDexterity, requiredIntelligence = requiredIntelligence)

            "accessory" -> Accessory(
                slot = slot, code = code, rarity = rarity, itemLevel = itemLevel,
                fixedModifierCodes = fixed, modifierPools = affixPools, baseParams = base,
                requiredLevel = requiredLevel, requiredStrength = requiredStrength,
                requiredDexterity = requiredDexterity, requiredIntelligence = requiredIntelligence)

            "armor" -> Armor(
                slot = slot, code = code, rarity = rarity, itemLevel = itemLevel,
                fixedModifierCodes = fixed, modifierPools = affixPools, baseParams = base,
                requiredLevel = requiredLevel, requiredStrength = requiredStrength,
                requiredDexterity = requiredDexterity, requiredIntelligence = requiredIntelligence)

            else -> throw EquipmentExceptions.funException("toEquipment", "Unknown equipment type: $type")
        }
    }
}

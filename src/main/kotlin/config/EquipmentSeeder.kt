package config

import application.enums.EnumEquipmentType
import application.enums.EnumRarity
import base.exception.model.EquipmentExceptions
import base.exception.model.ModifierExceptions
import features.data.equipment.equipment_data.Equipment
import features.data.equipment.equipment_data.Armor
import features.data.equipment.equipment_data.Accessory
import features.data.equipment.equipment_data.Weapon
import application.enums.EnumEquipmentWeapon
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import extensions.toStableObjectId
import features.logic.modifiers.Modifier
import features.logic.modifiers.ModifierDefinition

/**
 * Начальные данные коллекции `Equipment`.
 *
 * Шаблоны ссылаются на модификаторы по их _id, поэтому сидер создаётся
 * уже с сохранёнными описаниями модификаторов.
 *
 * Пул модификаторов, как в POE, задаётся слотом (`content/pools.json`), а локальные
 * аффиксы защиты и урона - самой базой: броневой шлем роллит броню, шлем уклонения -
 * уклонение. Шаблоны лежат данными в `resources/content/equipment.json` - см. [ContentResource].
 *
 * Уникальные предметы живут в [UniqueEquipmentSeeder] и в файл не уехали: каждая
 * уникалка порождает собственные описания модификаторов с диапазонами тиров,
 * то есть неотделима от генератора модификаторов.
 *
 * @param definitions документы коллекции `ModifierDefinition`
 */
class EquipmentSeeder(definitions: List<ModifierDefinition>) {

    companion object {
        /**
         * Файл с обычными шаблонами экипировки.
         */
        const val FILE = "equipment.json"

        /**
         * Файл с пулами модификаторов по слотам.
         */
        const val POOLS_FILE = "pools.json"
    }

    private val json = Json { ignoreUnknownKeys = true }


    private val byCode: Map<String, ModifierDefinition> = definitions.associateBy { it.code }

    /**
     * Описания, которые роллятся случайно и занимают префикс или суффикс предмета.
     *
     * Модификаторы влияния и верстака сюда не входят: первые открывает предмету его
     * влияние, вторые ставит только верстак, и ни те ни другие шаблону не принадлежат.
     */
    private val rollable = definitions.filter { it.isNaturalAffix() }

    /**
     * Ссылка на описание модификатора по его коду.
     */
    private fun mod(code: String): String =
        byCode[code]?._id ?: throw ModifierExceptions.funExceptionCodeNotFound("mod", code)

    /**
     * Именованные пулы из `content/pools.json`: пул слота, как в POE, - кольцо не роллит
     * скорость передвижения, а сапоги не роллят урон от заклинаний. С 0.24.0 это список
     * кодов, а не теги: у каждого слота свой набор, и файл читается глазами.
     */
    private val pools: Map<String, List<String>> by lazy {
        json.decodeFromString(PoolsDocument.serializer(), ContentResource.read(POOLS_FILE)).pools
    }

    private fun namedPool(name: String): MutableList<String> =
        pools[name]?.mapTo(mutableListOf(), ::mod)
            ?: throw EquipmentExceptions.funException("namedPool", "Unknown modifier pool: $name")

    /**
     * Локальные аффиксы, которые подходят базе: каждый стат, который они меняют, у базы есть.
     *
     * Так броня роллит только броню, гибрид брони и уклонения - обе и их смесь, а оружие -
     * свой физический урон и скорость атаки. Пул слота этого не знает: тип защиты задаёт база.
     */
    private fun localPool(base: List<Modifier>): List<String> {
        val stats = base.flatMap { modifier -> byId[modifier.modifierId]?.stats().orEmpty() }.toSet()
        return rollable.filter { it.isLocal && it.stats().all { stat -> stat in stats } }.map { it._id }
    }

    private val byId: Map<String, ModifierDefinition> = definitions.associateBy { it._id }

    fun seed(): ArrayList<Equipment> {
        val list = ArrayList<Equipment>()

        seedFromFile(list)
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

    // ==================== Чтение содержимого ====================

    @Serializable
    private data class BaseParam(val code: String, val values: List<Double> = emptyList())

    /**
     * Шаблон в том виде, в каком он лежит в файле.
     */
    @Serializable
    private data class EquipmentRecord(
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
        val modifierPool: String,
        val extraModifiers: List<String> = emptyList(),
    )

    @Serializable
    private data class PoolsDocument(val pools: Map<String, List<String>> = emptyMap())

    @Serializable
    private data class EquipmentDocument(val equipment: List<EquipmentRecord> = emptyList())

    private fun EquipmentRecord.toEquipment(): Equipment {
        val base = baseParams.mapTo(mutableListOf()) { Modifier.passive(mod(it.code), it.values) }
        val modifierIds = (namedPool(modifierPool) + localPool(base) + extraModifiers.map(::mod)).distinct().toMutableList()

        return when (type) {
            "weapon" -> Weapon(
                slot = slot, weaponType = weaponType ?: EnumEquipmentWeapon.BLADE, durability = durability,
                code = code, rarity = rarity, itemLevel = itemLevel, modifierIds = modifierIds, baseParams = base,
                requiredLevel = requiredLevel, requiredStrength = requiredStrength,
                requiredDexterity = requiredDexterity, requiredIntelligence = requiredIntelligence)

            "accessory" -> Accessory(
                slot = slot, code = code, rarity = rarity, itemLevel = itemLevel,
                modifierIds = modifierIds, baseParams = base,
                requiredLevel = requiredLevel, requiredStrength = requiredStrength,
                requiredDexterity = requiredDexterity, requiredIntelligence = requiredIntelligence)

            "armor" -> Armor(
                slot = slot, code = code, rarity = rarity, itemLevel = itemLevel,
                modifierIds = modifierIds, baseParams = base,
                requiredLevel = requiredLevel, requiredStrength = requiredStrength,
                requiredDexterity = requiredDexterity, requiredIntelligence = requiredIntelligence)

            else -> throw EquipmentExceptions.funException("toEquipment", "Unknown equipment type: $type")
        }
    }

    private fun seedFromFile(list: ArrayList<Equipment>) {
        val document = json.decodeFromString(EquipmentDocument.serializer(), ContentResource.read(FILE))
        document.equipment.forEach { list.add(it.toEquipment()) }
    }
}

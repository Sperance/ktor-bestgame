package features.equipment

import application.enums.EnumEquipmentType
import application.enums.EnumRarity
import application.model.ParamsStock
import application.model.Stat
import base.annotations.Immutable
import base.annotations.ReadOnly
import base.annotations.Required
import base.model.BaseEntity
import base.table.BaseTable
import features.characters.CharacterTable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.json.jsonb

/**
 * Таблица экипировки.
 *
 * Каждая строка — уникальный экземпляр предмета со своим набором характеристик.
 * Два "одинаковых" шлема могут иметь разные статы, баффы, уровень улучшения и т.д.
 *
 * ## Связи
 * - `character_id` — владелец предмета (FK → character)
 * - `equipped_slot` — если надет, в каком слоте; null = в инвентаре
 *
 * ## Пример записи
 * ```
 * id=1, name="Стальной шлем", slot=HELMET, rarity=RARE,
 * characterId=5, equippedSlot=HELMET,
 * stats=[{STR, FLAT, 10}, {CRIT_CHANCE, PERCENT, 5}]
 * ```
 */
object EquipmentTable : BaseTable("equipment") {

    /** Название предмета (видимое игроку) */
    val name = varchar("name", 100)

    /** Слот, в который может быть надет предмет */
    val slot = enumeration("slot", EnumEquipmentType::class)

    /** Редкость предмета */
    val rarity = enumeration("rarity", EnumRarity::class)

    /** Уровень предмета (влияет на базовые статы) */
    val itemLevel = integer("item_level").default(1)

    /** Уровень улучшения (+0, +1, +2...) */
    val enhanceLevel = integer("enhance_level").default(0)

    /** Владелец — персонаж */
    val characterId = long("character_id").references(CharacterTable.id)

    /**
     * В каком слоте надет. null = лежит в инвентаре (не экипирован).
     * Позволяет быстро найти: "что надето в слоте HELMET у персонажа X?"
     */
    val equippedSlot = enumeration("equipped_slot", EnumEquipmentType::class).nullable()

    val price = ulong("price")

    /**
     * Характеристики предмета (сила, ловкость, крит и т.д.)
     * Хранятся как JSONB: ["A1:1:10.0", "C0:2:5.0"]
     * Каждый ParamsStock = ключ + тип (flat/percent) + значение
     */
    val stats = jsonb<MutableSet<ParamsStock>>(
        name = "stats",
        jsonConfig = Json
    )

    /**
     * Дополнительные баффы/модификаторы (зачарования, гемы и т.д.)
     * Отделены от основных stats, чтобы можно было снять/поменять баффы
     * не затрагивая базовые характеристики предмета.
     */
    val buffs = jsonb<MutableSet<Stat>>(
        name = "buffs",
        jsonConfig = Json
    ).nullable()

    /** Текстовое описание / лор предмета */
    val description = varchar("description", 500).nullable()
}

/**
 * Экземпляр экипировки.
 *
 * Каждый предмет уникален — даже два "Стальных шлема" могут иметь разные статы.
 *
 * ```kotlin
 * val helmet = Equipment(
 *     name = "Стальной шлем",
 *     slot = EnumEquipmentType.HELMET,
 *     rarity = EnumRarity.RARE,
 *     characterId = 5,
 *     stats = mutableSetOf(
 *         Stat(EnumStatKey.STR, EnumStatType.FLAT, 10.0),
 *         Stat(EnumStatKey.CRIT_CHANCE, EnumStatType.PERCENT, 5.0)
 *     )
 * )
 * ```
 */
@Serializable
data class Equipment(
    @ReadOnly
    override val id: Long = -1,

    @Required
    val name: String = "",

    @Required
    val slot: EnumEquipmentType = EnumEquipmentType.UNDEFINED,

    val rarity: EnumRarity = EnumRarity.COMMON,

    @Required
    val itemLevel: Int = 1,

    val enhanceLevel: Int = 0,

    /** Владелец */
    @Immutable
    val characterId: Long,

    /** Слот, в котором надет. null = в инвентаре */
    val equippedSlot: EnumEquipmentType? = null,

    val price: ULong = 0u,

    /** Основные характеристики предмета */
    val stats: MutableSet<ParamsStock> = mutableSetOf(),

    /** Баффы / зачарования / модификаторы */
    val buffs: MutableSet<Stat>? = null,

    val description: String? = null,

    @ReadOnly
    override val version: Long = 1,

    @ReadOnly
    val createdAt: String? = null,

    @ReadOnly
    val updatedAt: String? = null
) : BaseEntity

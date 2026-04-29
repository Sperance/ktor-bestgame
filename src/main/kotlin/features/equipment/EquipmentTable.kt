package features.equipment

import application.enums.EnumEquipmentType
import application.enums.EnumRarity
import application.model.StatModifier
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
 * Каждая строка — уникальный экземпляр предмета со своим набором модификаторов.
 *
 * ## Система модификаторов (PoE-стиль)
 * - [implicit]   — врождённые свойства базового типа предмета (не меняются крафтом)
 * - [modifiers]  — явные аффиксы (PREFIX/SUFFIX/CRAFTED/ENCHANT)
 *
 * ## Связи
 * - `character_id`  — владелец (FK → character)
 * - `equipped_slot` — в каком слоте надет; null = в инвентаре
 */
object EquipmentTable : BaseTable("equipment") {

    val name = varchar("name", 100)
    val slot = enumeration("slot", EnumEquipmentType::class)
    val rarity = enumeration("rarity", EnumRarity::class)
    val itemLevel = integer("item_level").default(1)
    val enhanceLevel = integer("enhance_level").default(0)
    val characterId = long("character_id").references(CharacterTable.id)
    val equippedSlot = enumeration("equipped_slot", EnumEquipmentType::class).nullable()
    val price = ulong("price")

    /**
     * Врождённые имплицитные статы — определяются базой предмета (слотом).
     * Не изменяются перекаткой или крафтом.
     * Формат: JSONB массив compact-строк StatModifier.
     */
    val implicit = jsonb<MutableSet<StatModifier>>(
        name = "implicit",
        jsonConfig = Json
    ).nullable()

    /**
     * Явные аффиксы (PREFIX / SUFFIX / CRAFTED / ENCHANT / CORRUPTED).
     * Генерируются случайно при создании предмета. Перекатываются крафтом.
     * Формат: JSONB массив compact-строк StatModifier.
     */
    val modifiers = jsonb<MutableSet<StatModifier>>(
        name = "modifiers",
        jsonConfig = Json
    ).nullable()

    val description = varchar("description", 1000).nullable()
}

/**
 * Экземпляр предмета экипировки.
 *
 * Каждый предмет уникален — два "одинаковых" шлема могут иметь разные моды и тиры.
 *
 * @param implicit   Имплицитные статы (врождённые для типа предмета)
 * @param modifiers  Явные аффиксы (PREFIX / SUFFIX / CRAFTED и т.д.)
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

    @Immutable
    val characterId: Long = -1,

    val equippedSlot: EnumEquipmentType? = null,

    val price: ULong = 0u,

    /** Имплицитные моды (врождённые свойства) */
    val implicit: MutableSet<StatModifier>? = null,

    /** Явные аффиксы (PREFIX / SUFFIX и т.д.) */
    val modifiers: MutableSet<StatModifier>? = null,

    val description: String? = null,

    @ReadOnly
    override val version: Long = 1,

    @ReadOnly
    val createdAt: String? = null,

    @ReadOnly
    val updatedAt: String? = null
) : BaseEntity {

    /** Все моды предмета (implicit + explicit) для расчёта статов */
    fun allModifiers(): List<StatModifier> =
        (implicit ?: emptySet<StatModifier>()).toList() +
            (modifiers ?: emptySet<StatModifier>()).toList()

    /** Количество PREFIX аффиксов */
    fun prefixCount() = modifiers?.count { it.category.name == "PREFIX" } ?: 0

    /** Количество SUFFIX аффиксов */
    fun suffixCount() = modifiers?.count { it.category.name == "SUFFIX" } ?: 0
}

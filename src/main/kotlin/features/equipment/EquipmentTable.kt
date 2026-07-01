package features.equipment

import application.enums.EnumEquipmentType
import application.enums.EnumRarity
import application.model.Stat
import kotlinx.serialization.Serializable

@Serializable
data class Equipment(
    val id: Long = -1,
    val name: String = "",
    val slot: EnumEquipmentType = EnumEquipmentType.UNDEFINED,
    val rarity: EnumRarity = EnumRarity.COMMON,
    val itemLevel: Int = 1,
    val enhanceLevel: Int = 0,
    val characterId: Long,

    /** Слот, в котором надет. null = в инвентаре */
    val equippedSlot: EnumEquipmentType? = null,

    val price: ULong = 0u,

    /** Основные характеристики предмета */
    val stats: MutableSet<Stat> = mutableSetOf(),

    /** Баффы / зачарования / модификаторы */
    val buffs: MutableSet<Stat>? = null,

    val description: String? = null,

    val version: Long = 1,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

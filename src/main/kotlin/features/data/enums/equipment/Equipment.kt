package features.data.enums.equipment

import application.enums.EnumEquipmentType
import application.enums.EnumRarity
import application.model.Stat
import base.entity.StockEntity
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class Equipment(
    var slot: EnumEquipmentType,

    var name: String = "",
    var rarity: EnumRarity = EnumRarity.COMMON,
    var itemLevel: Int = 1,
    var enhanceLevel: Int = 0,
    var price: Long = 0,
    var stats: MutableSet<Stat> = mutableSetOf(),
    var buffs: MutableSet<Stat> = mutableSetOf(),
    var description: String = "",

    @Contextual
    override var _id: ObjectId = ObjectId(),
) : StockEntity
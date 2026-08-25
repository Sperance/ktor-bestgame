package features.data.equipmentName

import application.enums.EnumEquipmentType
import application.enums.EnumRarity
import base.entity.StockEntity
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class EquipmentName(
    var name: String,
    var type: EnumEquipmentType,
    var rarity: EnumRarity,

    override var _id: String = ObjectId().toHexString(),
) : StockEntity
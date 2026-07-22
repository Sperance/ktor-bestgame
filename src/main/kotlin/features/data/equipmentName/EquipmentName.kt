package features.data.equipmentName

import application.enums.EnumEquipmentType
import application.enums.EnumRarity
import base.entity.StockEntity
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class EquipmentName(
    var name: String,
    var type: EnumEquipmentType,
    var rarity: EnumRarity,

    @Contextual
    override var _id: ObjectId = ObjectId(),
) : StockEntity
package features.data.equipment

import application.enums.EnumEquipmentType
import application.enums.EnumRarity
import base.entity.StockEntity
import features.data.character.ModificationValue
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

interface EquipmentInterface {
    var slot: EnumEquipmentType
    var name: String
    var rarity: EnumRarity
    var itemLevel: Int
    var enhanceLevel: Int
    var price: Long
    var description: String
}

@Serializable
sealed class Equipment(
    @Contextual
    override var _id: ObjectId = ObjectId(),
    var params: MutableList<ModificationValue> = mutableListOf(),
) : StockEntity, EquipmentInterface
package features.data.equipment

import application.enums.EnumEquipmentType
import application.enums.EnumRarity
import base.entity.StockEntity
import features.data.character.ModificationValue
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
    override var _id: String = ObjectId().toHexString(),
    var params: List<ModificationValue> = listOf(),
) : StockEntity, EquipmentInterface
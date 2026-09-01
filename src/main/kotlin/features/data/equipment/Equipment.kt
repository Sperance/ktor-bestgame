package features.data.equipment

import application.enums.EnumEquipmentType
import application.enums.EnumRarity
import base.entity.StockEntity
import features.logic.modifiers.Modifier
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

interface EquipmentInterface {
    var slot: EnumEquipmentType
    var name: String
    var rarity: EnumRarity
    var itemLevel: Int
    var description: String
    var image: String?
    var implicitModifiers: ArrayList<Modifier>?
    var modifiers: ArrayList<Modifier>?
}

@Serializable
sealed class Equipment(
    override var _id: String = ObjectId().toHexString(),

    var price: Long = 1L

) : StockEntity, EquipmentInterface {
    open fun calculatePrice(): Long {
        var result = 0L
        result += (itemLevel * 50)
        result += ((implicitModifiers?.size ?: (0 * 100)))
        result += ((modifiers?.size ?: (0 * 150)))
        result += ((rarity.ordinal + 1) * 300)
        return result
    }
}
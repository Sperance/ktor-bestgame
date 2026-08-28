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
    var enhanceLevel: Int
    var price: Long
    var description: String
}

@Serializable
sealed class Equipment(
    override var _id: String = ObjectId().toHexString(),

    /**
     * Встроенные модификаторы базового предмета.
     */
    open var implicitModifiers: ArrayList<Modifier> = arrayListOf(),

    /**
     * Явные модификаторы предмета.
     *
     * PREFIX + SUFFIX + UNIQUE и т.д.
     */
    open var modifiers: ArrayList<Modifier> = arrayListOf(),

    override var price: Long = 1L,
    override var description: String = ""

) : StockEntity, EquipmentInterface
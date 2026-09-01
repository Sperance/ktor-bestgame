package features.data.equipment

import application.enums.EnumEquipmentType
import application.enums.EnumRarity
import features.logic.modifiers.Modifier
import kotlinx.serialization.Serializable

@Serializable
data class Accessory(
    override var slot: EnumEquipmentType,
    override var name: String = "",
    override var rarity: EnumRarity = EnumRarity.COMMON,
    override var itemLevel: Int = 1,
    override var image: String? = null,
    override var description: String = "",
    override var implicitModifiers: ArrayList<Modifier>? = null,
    override var modifiers: ArrayList<Modifier>? = null,
) : Equipment() {
    init {
        price = calculatePrice()
    }
}
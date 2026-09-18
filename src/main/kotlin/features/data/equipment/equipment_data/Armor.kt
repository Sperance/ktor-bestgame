package features.data.equipment.equipment_data

import application.enums.EnumEquipmentType
import application.enums.EnumRarity
import kotlinx.serialization.Serializable

@Serializable
data class Armor(
    override var slot: EnumEquipmentType,
    var defense: Int,
    override var name: String = "",
    override var rarity: EnumRarity = EnumRarity.COMMON,
    override var itemLevel: Int = 1,
    override var image: String? = null,
    override var description: String = "",
    override var modifierIds: MutableList<String> = mutableListOf(),
) : Equipment() {

    init {
        price = calculatePrice()
    }

    override fun calculatePrice(): Long {
        val newPrice = defense * 15
        return super.calculatePrice() + newPrice
    }
}
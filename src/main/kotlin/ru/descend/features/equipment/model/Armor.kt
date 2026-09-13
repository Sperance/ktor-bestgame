package ru.descend.features.equipment.model

import kotlinx.serialization.Serializable
import ru.descend.domain.enums.EnumEquipmentType
import ru.descend.domain.enums.EnumRarity
import ru.descend.domain.modifiers.Modifier
import ru.descend.domain.modifiers.ModifierDefinition

@Serializable
@kotlinx.serialization.SerialName("features.data.equipment.equipment_data.Armor")
data class Armor(
    override var slot: EnumEquipmentType,
    var defense: Int,
    override var name: String = "",
    override var rarity: EnumRarity = EnumRarity.COMMON,
    override var itemLevel: Int = 1,
    override var image: String? = null,
    override var description: String = "",
    override var modifiers: ArrayList<Modifier>? = null,
    override var modifierDefinitions: List<ModifierDefinition>? = null,
    override var modifierDefinitionsStock: List<ModifierDefinition>? = null
) : Equipment() {

    init {
        price = calculatePrice()
    }

    override fun calculatePrice(): Long {
        val newPrice = defense * 15L
        return super.calculatePrice() + newPrice
    }
}

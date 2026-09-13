package ru.descend.features.equipment.model

import ru.descend.domain.enums.EnumEquipmentType
import ru.descend.domain.enums.EnumRarity
import ru.descend.domain.modifiers.Modifier
import ru.descend.domain.modifiers.ModifierDefinition
import kotlinx.serialization.Serializable

@Serializable
@kotlinx.serialization.SerialName("features.data.equipment.equipment_data.Accessory")
data class Accessory(
    override var slot: EnumEquipmentType,
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
}

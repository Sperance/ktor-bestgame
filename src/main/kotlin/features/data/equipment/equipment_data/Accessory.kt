package features.data.equipment.equipment_data

import application.enums.EnumEquipmentType
import application.enums.EnumModifierDefinitions
import application.enums.EnumRarity
import features.logic.modifiers.Modifier
import features.logic.modifiers.ModifierDefinition
import kotlinx.serialization.Serializable

@Serializable
data class Accessory(
    override var slot: EnumEquipmentType,
    override var name: String = "",
    override var rarity: EnumRarity = EnumRarity.COMMON,
    override var itemLevel: Int = 1,
    override var image: String? = null,
    override var description: String = "",
    override var modifiers: ArrayList<Modifier>? = null,
    override var modifierDefinitions: List<EnumModifierDefinitions>? = null,
    override var modifierDefinitionsStock: List<EnumModifierDefinitions>? = null,
) : Equipment() {
    init {
        price = calculatePrice()
    }
}
package features.data.equipment.equipment_data

import application.enums.EnumEquipmentType
import application.enums.EnumRarity
import features.logic.modifiers.Modifier
import kotlinx.serialization.Serializable

/**
 * Броня. Её защита задаётся implicit-модификатором в [modifierIds],
 * отдельного поля под неё нет.
 */
@Serializable
data class Armor(
    override var slot: EnumEquipmentType,
    override var name: String = "",
    override var rarity: EnumRarity = EnumRarity.COMMON,
    override var itemLevel: Int = 1,
    override var image: String? = null,
    override var description: String = "",
    override var modifierIds: MutableList<String> = mutableListOf(),
    override var baseParams: MutableList<Modifier> = mutableListOf(),
    override var requiredLevel: Int = 1,
    override var requiredStrength: Int = 0,
    override var requiredDexterity: Int = 0,
    override var requiredIntelligence: Int = 0,
) : Equipment() {

    init {
        price = calculatePrice()
    }
}

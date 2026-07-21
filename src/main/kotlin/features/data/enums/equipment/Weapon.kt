package features.data.enums.equipment

import application.enums.EnumEquipmentType
import application.enums.EnumEquipmentWeapon
import application.enums.EnumRarity
import kotlinx.serialization.Serializable

@Serializable
data class Weapon(
    override var slot: EnumEquipmentType,
    var weaponType: EnumEquipmentWeapon,
    var damage: Int,
    var attackSpeed: Double,
    override var name: String = "",
    override var rarity: EnumRarity = EnumRarity.COMMON,
    override var itemLevel: Int = 1,
    override var enhanceLevel: Int = 0,
    override var price: Long = 0,
    override var description: String = "",
) : Equipment()
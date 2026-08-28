package features.data.equipment

import application.enums.EnumEquipmentType
import application.enums.EnumEquipmentWeapon
import application.enums.EnumRarity
import extensions.RandomExt
import kotlinx.serialization.Serializable

@Serializable
data class Weapon(
    override var slot: EnumEquipmentType,
    var weaponType: EnumEquipmentWeapon,
    var damage_min: Double,
    var damage_max: Double,
    var attackSpeed: Double,
    var durability: Int,

    override var name: String = "",
    override var rarity: EnumRarity = EnumRarity.COMMON,
    override var itemLevel: Int = 1,
    override var enhanceLevel: Int = 0,
) : Equipment() {

    /**
     * Получение конкретного урона (рандом между min и max)
     */
    fun calculateDamage(): Double {
        return RandomExt.randomDouble(damage_min, damage_max)
    }
}
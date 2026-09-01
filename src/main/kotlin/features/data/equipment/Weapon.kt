package features.data.equipment

import application.enums.EnumEquipmentType
import application.enums.EnumEquipmentWeapon
import application.enums.EnumRarity
import extensions.RandomExt
import features.logic.modifiers.Modifier
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
    override var image: String? = null,

    override var description: String = "",
    override var implicitModifiers: ArrayList<Modifier>? = null,
    override var modifiers: ArrayList<Modifier>? = null,
) : Equipment() {

    init {
        price = calculatePrice()
    }

    /**
     * Получение конкретного урона (рандом между min и max)
     */
    fun calculateDamage(): Double {
        return RandomExt.randomDouble(damage_min, damage_max)
    }

    override fun calculatePrice(): Long {
        val addPrice = ((damage_min + damage_max) * 10).toLong()
        return super.calculatePrice() + addPrice
    }
}
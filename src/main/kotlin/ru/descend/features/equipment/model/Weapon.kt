package ru.descend.features.equipment.model

import kotlinx.serialization.Serializable
import ru.descend.domain.enums.EnumEquipmentType
import ru.descend.domain.enums.EnumEquipmentWeapon
import ru.descend.domain.enums.EnumRarity
import ru.descend.domain.modifiers.Modifier
import ru.descend.domain.modifiers.ModifierDefinition
import ru.descend.shared.extensions.RandomExt

@Serializable
@kotlinx.serialization.SerialName("features.data.equipment.equipment_data.Weapon")
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
    override var modifiers: ArrayList<Modifier>? = null,
    override var modifierDefinitions: List<ModifierDefinition>? = null,
    override var modifierDefinitionsStock: List<ModifierDefinition>? = null
) : Equipment() {

    init {
        price = calculatePrice()
    }

    fun calculateDamage(): Double {
        return RandomExt.randomDouble(damage_min, damage_max)
    }

    override fun calculatePrice(): Long {
        val addPrice = ((damage_min + damage_max) * 10).toLong()
        return super.calculatePrice() + addPrice
    }
}

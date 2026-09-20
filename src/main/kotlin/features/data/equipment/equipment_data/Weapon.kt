package features.data.equipment.equipment_data

import application.enums.EnumEquipmentType
import application.enums.EnumEquipmentWeapon
import application.enums.EnumRarity
import features.logic.modifiers.Modifier
import kotlinx.serialization.Serializable

/**
 * Оружие. Урон и скорость атаки задаются implicit-модификаторами
 * в [modifierIds], отдельных полей под них нет.
 */
@Serializable
data class Weapon(
    override var slot: EnumEquipmentType,
    var weaponType: EnumEquipmentWeapon,

    /**
     * Прочность. Не характеристика персонажа, поэтому остаётся полем предмета.
     */
    var durability: Int = 100,

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

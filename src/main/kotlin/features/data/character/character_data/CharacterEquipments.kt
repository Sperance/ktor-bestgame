package features.data.character.character_data

import features.data.equipment.equipment_data.Armor
import features.data.equipment.equipment_data.Equipment
import features.data.equipment.equipment_data.Accessory
import features.data.equipment.equipment_data.Weapon
import features.logic.modifiers.Modifier
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.bson.types.ObjectId

@Serializable
data class CharacterEquipments(
    var equipmentId: String,
    var params: MutableList<Modifier> = mutableListOf(),

    @Transient
    var rolledModifiers: MutableList<Modifier> = mutableListOf(),

    var uuid: String = ObjectId().toHexString(),
) {
    companion object {
        /**
         * Создаёт CharacterEquipments из Equipment с автоматическим роллом модификаторов.
         * Как в POE - при получении предмета сразу роллятся случайные модификаторы из диапазона.
         */
        fun fromEquipment(equipment: Equipment): CharacterEquipments {
            val rolledMods = mutableListOf<Modifier>()

            // Роллим случайные модификаторы в зависимости от типа предмета
            when (equipment) {
                is Armor -> {
                    equipment.rollModifiers(forceNew = true).let {
                        rolledMods.addAll(it)
                    }
                }
                is Weapon -> {
                    equipment.rollModifiers(forceNew = true).let {
                        rolledMods.addAll(it)
                    }
                }
                is Accessory -> {
                    equipment.rollModifiers(forceNew = true).let {
                        rolledMods.addAll(it)
                    }
                }
            }

            return CharacterEquipments(
                equipmentId = equipment._id,
                params = rolledMods
            )
        }
    }

    /**
     * Возвращает все модификаторы (implicit + rolled)
     */
    fun getAllModifiers(): List<Modifier> {
        return rolledModifiers + params
    }
}
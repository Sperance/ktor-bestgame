package features.data.character.character_data

import features.data.equipment.equipment_data.Armor
import features.data.equipment.equipment_data.Equipment
import features.data.equipment.equipment_data.Accessory
import features.data.equipment.equipment_data.Weapon
import features.logic.modifiers.Modifier
import features.logic.modifiers.WeightedModifierGenerator
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
    var poe: features.poe.PoeItem? = null,
) {
    companion object {
        /**
         * Создаёт CharacterEquipments из Equipment с автоматическим роллом модификаторов.
         * Как в POE - при получении предмета сразу роллятся случайные модификаторы из диапазона.
         */
        fun fromEquipment(equipment: Equipment): CharacterEquipments {
            equipment.poeBaseId?.let { baseId ->
                val catalog = features.poe.PoeCatalog.bundled
                val crafting = features.poe.PoeCrafting(catalog)
                return features.poe.PoeInventory(catalog, crafting).fromState(
                    crafting.generate(baseId, equipment.itemLevel, features.poe.PoeRarity.NORMAL))
            }
            // Serialize a copy: never roll into the shared cache/template instance.
            val json = server.addons.AppJson
            val copy = json.decodeFromString(Equipment.serializer(), json.encodeToString(Equipment.serializer(), equipment))
            return CharacterEquipments(equipmentId = equipment._id,
                params = copy.rollModifiers(WeightedModifierGenerator(), forceNew = true).toMutableList())
        }
    }

    /**
     * Возвращает все модификаторы (implicit + rolled)
     */
    fun getAllModifiers(): List<Modifier> {
        return rolledModifiers + params
    }
}
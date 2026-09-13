package ru.descend.features.character.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.bson.types.ObjectId
import ru.descend.domain.modifiers.Modifier
import ru.descend.domain.modifiers.WeightedModifierGenerator
import ru.descend.features.equipment.model.Equipment

@Serializable
@kotlinx.serialization.SerialName("features.data.character.character_data.CharacterEquipments")
data class CharacterEquipments(
    var equipmentId: String,
    var params: MutableList<Modifier> = mutableListOf(),

    @Transient
    var rolledModifiers: MutableList<Modifier> = mutableListOf(),

    var uuid: String = ObjectId().toHexString(),
    var poe: ru.descend.features.poe.domain.PoeItem? = null,
) {
    companion object {
        /**
         * Создаёт CharacterEquipments из Equipment с автоматическим роллом модификаторов.
         * Как в POE - при получении предмета сразу роллятся случайные модификаторы из диапазона.
         */
        fun fromEquipment(equipment: Equipment, catalog: ru.descend.features.poe.catalog.PoeCatalog? = null, definitions: List<ru.descend.domain.modifiers.ModifierDefinition> = emptyList()): CharacterEquipments {
            equipment.poeBaseId?.let { baseId ->
                val catalog = requireNotNull(catalog) { "PoE grants require a MongoDB catalog snapshot" }
                val crafting = ru.descend.features.poe.domain.PoeCrafting(catalog)
                return ru.descend.features.poe.domain.PoeInventory(catalog, crafting).fromState(
                    crafting.generate(baseId, equipment.itemLevel, if (catalog.unique(catalog.base(baseId))) ru.descend.features.poe.domain.PoeRarity.UNIQUE else ru.descend.features.poe.domain.PoeRarity.NORMAL, equipment.stockModifierDefinitionRefs))
            }
            // Serialize a copy: never roll into the shared cache/template instance.
            val json = ru.descend.infrastructure.http.AppJson
            val copy = json.decodeFromString(Equipment.serializer(), json.encodeToString(Equipment.serializer(), equipment))
            val byRef = definitions.associateBy { ru.descend.domain.modifiers.ModifierRef(it.id, it.revision) }
            copy.modifierDefinitions = equipment.modifierDefinitionRefs.map { requireNotNull(byRef[it]) { "Missing definition: $it" } }
            copy.modifierDefinitionsStock = equipment.stockModifierDefinitionRefs.map { requireNotNull(byRef[it]) { "Missing stock definition: $it" } }
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

package features.data.character.character_data

import features.logic.modifiers.Modifier
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class CharacterEquipments(
    var equipmentId: String,
    var equipmentType: String, // "weapon", "armor", "accessory"
    var params: MutableList<Modifier> = mutableListOf(),

    var uuid: String = ObjectId().toHexString(),
)
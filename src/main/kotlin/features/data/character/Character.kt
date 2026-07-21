package features.data.character

import extensions.now
import base.entity.VersionedEntity
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class Character(
    var userId: String,

    var name: String,
    var description: String = "",
    var level: Short = 1,
    var experience: Int = 0,
    var money: Long = 0,
    var params: ArrayList<ModificationValue> = arrayListOf(),
    var equipments: MutableSet<CharacterEquipments> = mutableSetOf(),

    @Contextual
    override var _id: ObjectId = ObjectId(),
    override var version: Long = 0,
    override var deleted: Boolean = false,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override val updatedAt: LocalDateTime = LocalDateTime.now(),
) : VersionedEntity

@Serializable
data class CharacterEquipments(
    var equipmentId: String,
    var equipmentType: String, // "weapon", "armor", "accessory"
    var params: ArrayList<ModificationValue> = arrayListOf(),
    var enabled: Boolean = true,

    @Contextual
    var uuid: ObjectId = ObjectId(),
)

//@Serializable
//data class Modification(
//    var name: String,
//    var stats: ArrayList<ModificationValue>,
//)
//
@Serializable
data class ModificationValue(
    var property_id: String,
    var level: Byte,
    var power: Double
)
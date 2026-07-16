package features.data.character

import application.model.Stat
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
    var params: MutableSet<Stat> = mutableSetOf(),
    var buffs: MutableSet<Stat> = mutableSetOf(),
    var equipments: MutableSet<CharacterEquipments> = mutableSetOf(),

    @Contextual
    override var _id: ObjectId = ObjectId(),
    override var version: Long = 0,
    override var deleted: Boolean = false,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override val updatedAt: LocalDateTime = LocalDateTime.now(),
) : VersionedEntity()

@Serializable
data class CharacterEquipments(
    var equipmentId: String,
    var enabled: Boolean = true,

    @Contextual
    var uuid: ObjectId = ObjectId(),
)
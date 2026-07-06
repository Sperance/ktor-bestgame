package features.equipment

import application.enums.EnumEquipmentType
import application.enums.EnumRarity
import application.model.Stat
import base.entity.VersionedEntity
import extensions.now
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class Equipment(
    var characterId: String,
    var slot: EnumEquipmentType,
    var equipped: Boolean = false,

    var name: String = "",
    var rarity: EnumRarity = EnumRarity.COMMON,
    var itemLevel: Int = 1,
    var enhanceLevel: Int = 0,
    var price: Long = 0,
    var stats: MutableSet<Stat> = mutableSetOf(),
    var buffs: MutableSet<Stat> = mutableSetOf(),
    var description: String = "",

    @Contextual
    override var _id: ObjectId = ObjectId(),
    override var version: Long = 0,
    override var deleted: Boolean = false,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override val updatedAt: LocalDateTime = LocalDateTime.now(),
) : VersionedEntity()
package features.data.blockList

import base.entity.VersionedEntity
import extensions.now
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class BlockList(
    var address: String,
    var hardware: String = "",
    var user_id: String? = null,
    var expiredAt: LocalDateTime,

    @Contextual
    override var _id: ObjectId = ObjectId(),
    override var version: Long = 0,
    override var deleted: Boolean = false,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override var updatedAt: LocalDateTime = LocalDateTime.now(),
) : VersionedEntity
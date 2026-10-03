package ru.descend.exileforge.features.data.blockList
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import ru.descend.exileforge.base.entity.VersionedEntity
import ru.descend.exileforge.extensions.now

@Serializable
data class BlockList(
    var address: String,
    var hardware: String = "",
    var user_id: String? = null,
    var expiredAt: LocalDateTime,

    override var _id: String = ObjectId().toHexString(),
    override var version: Long = 0,
    override var deleted: Boolean = false,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override var updatedAt: LocalDateTime = LocalDateTime.now(),
) : VersionedEntity

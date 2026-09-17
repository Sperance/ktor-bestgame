package ru.descend.features.blocklist.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import ru.descend.shared.extensions.now
import ru.descend.shared.model.VersionedEntity

@Serializable
@kotlinx.serialization.SerialName("features.data.blockList.BlockList")
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

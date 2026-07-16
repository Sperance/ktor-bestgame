package features.data.items

import extensions.now
import base.entity.VersionedEntity
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class Items(
    val name: String = "",
    val description: String? = null,
    val image: String? = null,
    val price: Long = 0,

    @Contextual
    override var _id: ObjectId = ObjectId(),
    override var version: Long = 0,
    override var deleted: Boolean = false,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override val updatedAt: LocalDateTime = LocalDateTime.now(),
) : VersionedEntity()
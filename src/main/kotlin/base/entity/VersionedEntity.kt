package base.entity

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
abstract class VersionedEntity {
    abstract var _id: ObjectId
    abstract var version: Long
    abstract var deleted: Boolean
    abstract val createdAt: LocalDateTime
    abstract val updatedAt: LocalDateTime

    fun getId(): String = _id.toHexString()
    fun setId(id: String) {
        _id = ObjectId(id)
    }
}
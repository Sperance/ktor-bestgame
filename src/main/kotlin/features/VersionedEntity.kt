package features

import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
abstract class VersionedEntity {
    abstract var _id: ObjectId
    abstract var version: Long
    abstract var deleted: Boolean
}
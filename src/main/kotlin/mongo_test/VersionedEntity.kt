package mongo_test

import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
abstract class VersionedEntity {
    abstract val _id: ObjectId
    abstract var version: Int
    abstract var deleted: Boolean
}
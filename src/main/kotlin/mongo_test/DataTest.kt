package mongo_test

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class UserMongo(
    @Contextual
    override val _id: ObjectId = ObjectId(),
    override var version: Int = 0,
    var email: String,
    var name: String,
    var age: Int
) : VersionedEntity()
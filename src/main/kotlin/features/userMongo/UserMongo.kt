package features.userMongo

import application.enums.EnumUserRoles
import extensions.ObjectIdSerializer
import extensions.now
import features.VersionedEntity
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class UserMongo(
    @Serializable(with = ObjectIdSerializer::class)
    override var _id: ObjectId = ObjectId(),
    override var version: Long = 0,
    override var deleted: Boolean = false,
    var name: String = "",
    var email: String = "",
    var login: String = "",
    var password: String = "",
    var salt: String = "",
    var age: Int? = null,
    var isActive: Boolean = true,
    var role: EnumUserRoles = EnumUserRoles.USER,
    var lastLoginDate: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
) : VersionedEntity() {
    fun id(): String = _id.toHexString()
}
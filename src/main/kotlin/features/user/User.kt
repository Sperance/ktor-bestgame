package features.user

import application.enums.EnumUserRoles
import extensions.now
import base.entity.VersionedEntity
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class User(
    var name: String = "",
    var email: String = "",
    var login: String = "",
    var password: String = "",
    var salt: String = "",
    var age: Int? = null,
    var isActive: Boolean = true,
    var role: EnumUserRoles = EnumUserRoles.USER,
    var lastLoginDate: LocalDateTime? = null,

    var countCharacters: Int = 0,

    @Contextual
    override var _id: ObjectId = ObjectId(),
    override var version: Long = 0,
    override var deleted: Boolean = false,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override val updatedAt: LocalDateTime = LocalDateTime.now(),
) : VersionedEntity()

@Serializable
data class UserResponse(
    val id: String,
    val version: Long,
    val name: String,
    val email: String,
    val login: String,
    val age: Int?,
    val isActive: Boolean,
    val role: EnumUserRoles,
    val lastLoginDate: LocalDateTime?,
    var countCharacters: Int,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

// Функция-расширение для маппинга
fun User.toResponse(): UserResponse = UserResponse(
    id = getId(),
    name = name,
    email = email,
    login = login,
    age = age,
    isActive = isActive,
    role = role,
    lastLoginDate = lastLoginDate,
    createdAt = createdAt,
    updatedAt = updatedAt,
    version = version,
    countCharacters = countCharacters
)
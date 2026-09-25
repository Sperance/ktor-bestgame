package features.data.user

import application.enums.EnumUserRoles
import extensions.now
import base.entity.VersionedEntity
import kotlinx.datetime.LocalDateTime
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
    var device_id: String = "",

    var countCharacters: Int = 0,
    /** Промокоды, взятые аккаунтом: отметка на аккаунте переживает удаление персонажа. */
    var redeemedCodes: MutableList<String> = mutableListOf(),

    override var _id: String = ObjectId().toHexString(),
    override var version: Long = 0,
    override var deleted: Boolean = false,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override var updatedAt: LocalDateTime = LocalDateTime.now(),
) : VersionedEntity

/** Аккаунт, как его видит клиент: только поля, которые он читает. */
@Serializable
data class UserResponse(
    val id: String,
    val version: Long,
    val name: String,
    val login: String,
    val isActive: Boolean,
    val role: EnumUserRoles,
    val countCharacters: Int,
)

fun User.toResponse(): UserResponse = UserResponse(
    id = _id,
    version = version,
    name = name,
    login = login,
    isActive = isActive,
    role = role,
    countCharacters = countCharacters,
)

/**
 * Ответ на вход: аккаунт и токен сессии.
 *
 * `device_id` в [UserResponse] больше нет: до 0.21.0 его отдавал открытый список
 * пользователей, а по нему же входили в аккаунт - два запроса, и ты любой игрок.
 */
@Serializable
data class LoginResponse(val user: UserResponse, val token: String)

/** Тело входа по логину и паролю. Пароль идёт в теле, а не в строке запроса. */
@Serializable
data class LoginRequest(val login: String, val password: String)

/** Тело входа и регистрации по устройству. */
@Serializable
data class DeviceRequest(val deviceId: String)

/** Тело смены пароля. */
@Serializable
data class PasswordChange(val password: String, val newPassword: String)

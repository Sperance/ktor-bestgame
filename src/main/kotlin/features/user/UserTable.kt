package features.user

import application.enums.EnumUserRoles
import base.annotations.Immutable
import base.annotations.ReadOnly
import base.annotations.WriteOnly
import base.model.BaseEntity
import base.table.BaseTable
import kotlinx.serialization.Serializable

object UsersTable : BaseTable("users") {
    val name = varchar("name", 255)
    val email = varchar("email", 255).uniqueIndex()
    val login = varchar("login", 255).uniqueIndex()
    val password = varchar("password", 1024)
    val salt = varchar("salt", 256)
    val age = integer("age").nullable()
    val isActive = bool("is_active").default(true)
    val role = enumerationByName("role", 20, EnumUserRoles::class).default(EnumUserRoles.USER)
}

@Serializable
data class User(
    @ReadOnly
    override val id: Long? = null,

    val name: String,

    val email: String,

    val login: String,

    @WriteOnly
    val password: String,

    @Immutable
    @WriteOnly
    val salt: String = "",

    val age: Int? = null,

    val isActive: Boolean = true,

    val role: EnumUserRoles = EnumUserRoles.USER,

    @ReadOnly
    override val version: Long = 1,

    @ReadOnly
    val createdAt: String? = null,

    @ReadOnly
    val updatedAt: String? = null
) : BaseEntity
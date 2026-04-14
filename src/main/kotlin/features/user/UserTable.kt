package features.user

import application.enums.EnumUserRoles
import base.annotations.ReadOnly
import base.annotations.Required
import base.model.BaseEntity
import base.table.BaseTable
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.javatime.datetime

object UsersTable : BaseTable("users") {
    val name = varchar("name", 255)
    val email = varchar("email", 255).uniqueIndex()
    val login = varchar("login", 255).uniqueIndex()
    val password = varchar("password", 1024)
    val salt = varchar("salt", 256)
    val age = integer("age").nullable()
    val isActive = bool("is_active").default(true)
    val role = enumerationByName("role", 20, EnumUserRoles::class).default(EnumUserRoles.USER)
    val lastLoginDate = datetime("last_login_date").nullable()
}

@Serializable
data class User(
    @ReadOnly
    override val id: Long? = null,

    @Required
    val name: String = "",

    @Required
    val email: String = "",

    @Required
    val login: String = "",

    @Required
    val password: String = "",

    val salt: String = "",

    val age: Int? = null,

    val isActive: Boolean = true,

    val role: EnumUserRoles = EnumUserRoles.USER,

    @ReadOnly
    val lastLoginDate: String? = null,

    @ReadOnly
    override val version: Long = 1,

    @ReadOnly
    val createdAt: String? = null,

    @ReadOnly
    val updatedAt: String? = null
) : BaseEntity
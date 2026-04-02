package features.user

import base.annotations.DefaultValue
import base.annotations.ReadOnly
import base.model.BaseEntity
import base.table.BaseTable
import kotlinx.serialization.Serializable

object UsersTable : BaseTable("users") {
    val name = varchar("name", 255)
    val email = varchar("email", 255).uniqueIndex()
    val age = integer("age").nullable()
    val isActive = bool("is_active").default(true)
}

@Serializable
data class User(
    @ReadOnly
    override val id: Long? = null,

    val name: String,

    val email: String,

    val age: Int? = null,

    @DefaultValue("true")
    val isActive: Boolean = true,

    @ReadOnly
    override val version: Long = 1,

    @ReadOnly
    val createdAt: String? = null,

    @ReadOnly
    val updatedAt: String? = null
) : BaseEntity
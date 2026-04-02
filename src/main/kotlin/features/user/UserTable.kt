package features.user

import base.table.BaseTable

object UsersTable : BaseTable("users") {
    val name = varchar("name", 255)
    val email = varchar("email", 255).uniqueIndex()
    val age = integer("age").nullable()
    val isActive = bool("is_active").default(true)
}
package features.user

import base.repository.BaseRepository
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Нет ни одного ручного маппинга полей —
 * insert/update/toEntity полностью через рефлексию.
 *
 * Здесь только кастомные запросы.
 */
class UserRepository : BaseRepository<UserResponse, CreateUserRequest, UpdateUserRequest, UsersTable>(
    table = UsersTable,
    entityClass = UserResponse::class
) {
    override val entityName = "User"

    fun findByEmail(email: String): UserResponse? = transaction {
        table.selectAll()
            .where { table.email eq email }
            .singleOrNull()
            ?.let(::toEntity)
    }

    fun searchByName(name: String): List<UserResponse> = transaction {
        table.selectAll()
            .where { table.name like "%$name%" }
            .orderBy(table.name, SortOrder.ASC)
            .map(::toEntity)
    }

    fun findActive(): List<UserResponse> = transaction {
        table.selectAll()
            .where { table.isActive eq true }
            .orderBy(table.id, SortOrder.ASC)
            .map(::toEntity)
    }
}

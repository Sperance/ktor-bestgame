package features.user

import base.repository.BaseRepository
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class UserRepository : BaseRepository<User, UsersTable>(
    table = UsersTable,
    entityClass = User::class
) {
    override val entityName = "User"

    /**
     * Очищает секретные поля перед отправкой клиенту.
     */
    override fun beforeResponse(entity: User) = entity.copy(
        password = "",
        salt = ""
    )

    fun findByEmail(email: String): User? = transaction {
        table.selectAll()
            .where { table.email eq email }
            .singleOrNull()
            ?.let(::toEntity)
    }

    fun searchByName(name: String): List<User> = transaction {
        table.selectAll()
            .where { table.name like "%$name%" }
            .map(::toEntity)
    }

    fun findActive(): List<User> = transaction {
        table.selectAll()
            .where { table.isActive eq true }
            .map(::toEntity)
    }

    fun findByLogin(login: String): User? = transaction {
        table.selectAll()
            .where { table.login eq login }
            .singleOrNull()
            ?.let(::toEntity)
    }

    /**
     * Возвращает (id, password_hash, salt) по login напрямую из БД,
     * минуя toEntity (который маскирует @WriteOnly-поля).
     *
     * Используется для аутентификации.
     */
    fun findCredentialsByLogin(login: String): Triple<Long, String, String>? = transaction {
        table.selectAll()
            .where { table.login eq login }
            .singleOrNull()
            ?.let { row ->
                Triple(row[table.id], row[table.password], row[table.salt])
            }
    }
}
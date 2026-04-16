package features.user

import base.exception.NotFoundException
import base.exception.OptimisticLockException
import base.repository.BaseRepository
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDateTime

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

    /**
     * Проставляет lastLoginDate = now() с соблюдением оптимистичной блокировки:
     * проверяет version, инкрементирует её и обновляет updatedAt —
     * ровно как BaseRepository.update().
     */
    fun touchLastLoginDate(id: Long, expectedVersion: Long): User {
        val updated = transaction {
            table.update({
                (table.id eq id) and (table.version eq expectedVersion)
            }) { stmt ->
                stmt[table.lastLoginDate] = LocalDateTime.now()
                stmt[table.version] = expectedVersion + 1
                stmt[table.updatedAt] = LocalDateTime.now()
            }
        }

        if (updated == 0) {
            if (!exists(id)) throw NotFoundException("$entityName(id=$id) not found")
            throw OptimisticLockException(entityName, id, expectedVersion)
        }

        return findById(id)!!
    }
}
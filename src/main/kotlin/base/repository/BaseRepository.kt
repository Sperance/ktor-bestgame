package base.repository

import base.exception.NotFoundException
import base.exception.OptimisticLockException
import base.model.BaseEntity
import base.model.CreateRequest
import base.model.UpdateRequest
import base.reflection.ReflectiveMapper
import base.table.BaseTable
import io.ktor.server.routing.get
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import kotlin.reflect.KClass

/**
 * Ни один наследник не пишет ни одного ручного маппинга полей.
 * Вся работа — рефлексия + кэш.
 *
 * @param E   Response Entity (implements BaseEntity)
 * @param CQ  Create Request DTO
 * @param UQ  Update Request DTO (implements UpdateRequest → содержит version)
 * @param T   Table (extends BaseTable)
 */
abstract class BaseRepository<E : BaseEntity, CQ : CreateRequest, UQ : UpdateRequest, T : BaseTable>(
    protected val table: T,
    private val entityClass: KClass<E>
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    /** Имя сущности для логов и сообщений об ошибках */
    protected open val entityName: String = entityClass.simpleName ?: "Entity"

    // ==================== Row → Entity ====================

    protected open fun toEntity(row: ResultRow): E =
        ReflectiveMapper.toEntity(entityClass, table, row)

    // ==================== READ ====================

    open fun findAll(): List<E> = transaction {
        table.selectAll()
            .orderBy(table.id, SortOrder.ASC)
            .map(::toEntity)
    }

    open fun findById(id: Long): E? = transaction {
        table.selectAll()
            .where { table.id eq id }
            .singleOrNull()
            ?.let(::toEntity)
    }

    open fun findPaged(page: Int, pageSize: Int): List<E> = transaction {
        table.selectAll()
            .orderBy(table.id, SortOrder.ASC)
            .limit(pageSize)
            .offset((page.toLong() * pageSize))
            .map(::toEntity)
    }

    open fun count(): Long = transaction {
        table.selectAll().count()
    }

    open fun exists(id: Long): Boolean = transaction {
        table.selectAll().where { table.id eq id }.count() > 0
    }

    // ==================== CREATE ====================

    open fun create(request: CQ): E = transaction {
        log.debug("Creating {}: {}", entityName, request)

        val insertedId = table.insert { stmt ->
            ReflectiveMapper.insertFromDto(stmt, table, request as Any)
            stmt[table.version] = 1L
            stmt[table.createdAt] = LocalDateTime.now()
            stmt[table.updatedAt] = LocalDateTime.now()
        } get table.id

        findById(insertedId)!!
    }

    open fun createBatch(requests: List<CQ>): List<E> = transaction {
        val now = LocalDateTime.now()
        val ids = requests.map { req ->
            table.insert { stmt ->
                ReflectiveMapper.insertFromDto(stmt, table, req as Any)
                stmt[table.version] = 1L
                stmt[table.createdAt] = now
                stmt[table.updatedAt] = now
            } get table.id
        }
        ids.mapNotNull { findById(it) }
    }

    // ==================== UPDATE (Optimistic Locking) ====================

    /**
     * WHERE id = :id AND version = :expectedVersion
     * SET   version = version + 1, updated_at = now(), ...
     *
     * Если 0 строк обновлено:
     *   - Сущность удалена → NotFoundException
     *   - version изменилась → OptimisticLockException
     */
    open fun update(id: Long, request: UQ): E {
        val expectedVersion = request.version

        val updatedRows = transaction {
            table.update({
                (table.id eq id) and (table.version eq expectedVersion)
            }) { stmt ->
                ReflectiveMapper.partialUpdateFromDto(stmt, table, request as Any)
                stmt[table.version] = expectedVersion + 1
                stmt[table.updatedAt] = LocalDateTime.now()
            }
        }

        if (updatedRows == 0) {
            throwLockOrNotFound(id, expectedVersion)
        }

        log.debug("Updated $entityName(id=$id): version $expectedVersion → ${expectedVersion + 1}")
        return findById(id)!!
    }

    // ==================== DELETE ====================

    open fun delete(id: Long): Boolean = transaction {
        table.deleteWhere { table.id eq id } > 0
    }

    /**
     * DELETE с optimistic locking.
     */
    open fun deleteWithVersion(id: Long, expectedVersion: Long): Boolean {
        val deleted = transaction {
            table.deleteWhere {
                (table.id eq id) and (table.version eq expectedVersion)
            }
        }
        if (deleted == 0) {
            throwLockOrNotFound(id, expectedVersion)
        }
        log.debug("Deleted $entityName(id=$id, version=$expectedVersion)")
        return true
    }

    open fun deleteAll(): Int = transaction {
        table.deleteAll()
    }

    // ==================== Internal ====================

    private fun throwLockOrNotFound(id: Long, expectedVersion: Long): Nothing {
        if (!exists(id)) {
            throw NotFoundException("$entityName(id=$id) not found")
        }
        throw OptimisticLockException(entityName, id, expectedVersion)
    }
}
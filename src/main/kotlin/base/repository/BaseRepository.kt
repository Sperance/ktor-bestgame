package base.repository

import base.exception.BadRequestException
import base.exception.NotFoundException
import base.exception.OptimisticLockException
import base.model.BaseEntity
import base.reflection.ReflectiveMapper
import base.table.BaseTable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
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
 * Теперь принимает сырой JsonObject.
 * Не нужны отдельные CreateRequest / UpdateRequest классы.
 *
 * @param E — Entity (единственная модель)
 * @param T — Table
 */
abstract class BaseRepository<E : BaseEntity, T : BaseTable>(
    protected val table: T,
    protected val entityClass: KClass<E>
) {

    private val log = LoggerFactory.getLogger(this::class.java)
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
            .offset(page.toLong() * pageSize)
            .map(::toEntity)
    }

    open fun count(): Long = transaction { table.selectAll().count() }

    open fun exists(id: Long): Boolean = transaction {
        table.selectAll().where { table.id eq id }.count() > 0
    }

    // ==================== CREATE ====================

    open fun create(json: JsonObject): E = transaction {
        log.debug("Creating {} from JSON: {}", entityName, json)

        val insertedId = table.insert { stmt ->
            ReflectiveMapper.insertFromJson(stmt, table, json, entityClass)
            stmt[table.version] = 1L
            stmt[table.createdAt] = LocalDateTime.now()
            stmt[table.updatedAt] = LocalDateTime.now()
        } get table.id

        findById(insertedId)!!
    }

    // ==================== UPDATE (Optimistic Locking) ====================

    open fun update(id: Long, json: JsonObject): E {
        var expectedVersion: Long = -1

        val updatedRows = transaction {
            table.update({
                // Сначала парсим version из json внутри update-лямбды
                // Но нам нужен version до построения WHERE... Делаем в два шага:
                expectedVersion = extractVersion(json)
                (table.id eq id) and (table.version eq expectedVersion)
            }) { stmt ->
                ReflectiveMapper.updateFromJson(stmt, table, json, entityClass)
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

    open fun deleteWithVersion(id: Long, expectedVersion: Long): Boolean {
        val deleted = transaction {
            table.deleteWhere {
                (table.id eq id) and (table.version eq expectedVersion)
            }
        }
        if (deleted == 0) throwLockOrNotFound(id, expectedVersion)
        return true
    }

    open fun deleteAll(): Int = transaction { table.deleteAll() }

    // ==================== Internal ====================

    private fun extractVersion(json: JsonObject): Long {
        val element = json["version"]
            ?: throw BadRequestException("'version' is required for update")
        return element.jsonPrimitive.longOrNull
            ?: throw BadRequestException("'version' must be a number")
    }

    private fun throwLockOrNotFound(id: Long, expectedVersion: Long): Nothing {
        if (!exists(id)) throw NotFoundException("$entityName(id=$id) not found")
        throw OptimisticLockException(entityName, id, expectedVersion)
    }
}
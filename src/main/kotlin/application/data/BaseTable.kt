package application.data

import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant

interface BaseDao<E : BaseEntity<*>> {
    fun findById(id: Long): E?
    fun findById(id: EntityID<Long>): E?
    fun findAll(limit: Int? = null): List<E>
    fun findWhere(condition: Op<Boolean>): List<E>
    fun findOne(condition: Op<Boolean>): E?
    fun count(condition: Op<Boolean> = Op.TRUE): Long
    fun exists(condition: Op<Boolean>): Boolean

    fun create(body: E.() -> Unit): E

    fun update(entity: E, body: E.() -> Unit): E

    fun softDelete(entity: E): E
    fun hardDelete(id: Long): Boolean
}

open class BaseDTO(
    var _createdAt: Instant = Instant.now(),
    var _updatedAt: Instant = Instant.now(),
    var _deletedAt: Instant? = null,
    var _version: Long = 0
)

abstract class BaseTable(name: String = "") : LongIdTable(name) {
    val createdAt = timestamp("created_at").default(Instant.now())
    val updatedAt = timestamp("updated_at").default(Instant.now())
    val deletedAt = timestamp("deleted_at").nullable()
    val version = long("version").default(0)
}

abstract class BaseEntity<SNAPSHOT : BaseDTO>(id: EntityID<Long>, table: BaseTable) : LongEntity(id) {
    var createdAt by table.createdAt
    var updatedAt by table.updatedAt
    var deletedAt by table.deletedAt
    var version by table.version

    abstract fun toSnapshot(): SNAPSHOT
}

abstract class ExposedBaseDao<T : BaseTable, E : BaseEntity<*>>(
    protected val table: T,
    protected val entityClass: LongEntityClass<E>
) : BaseDao<E> {

    // CREATE: возвращает E
    override fun create(body: E.() -> Unit): E {
        val entityResult = entityClass.new {
            body()
        }
        return entityResult
    }

    // READ
    override fun findById(id: Long): E? =
        entityClass.findById(id)?.takeIf { it.deletedAt == null }

    override fun findById(id: EntityID<Long>): E? =
        entityClass.findById(id)?.takeIf { it.deletedAt == null }

    override fun findAll(limit: Int?): List<E> =
        if (limit != null) {
            entityClass.all()
                .limit(limit)
                .filter { it.deletedAt == null }
                .toList()
        } else {
            entityClass.all()
                .filter { it.deletedAt == null }
                .toList()
        }

    override fun findWhere(condition: Op<Boolean>): List<E> =
        entityClass.find { condition and table.deletedAt.isNull() }.toList()

    override fun findOne(condition: Op<Boolean>): E? =
        entityClass.find {
            condition and table.deletedAt.isNull()
        }.firstOrNull()

    override fun count(condition: Op<Boolean>): Long =
        table.select(condition and table.deletedAt.isNull()).count()

    override fun exists(condition: Op<Boolean>): Boolean =
        table.select(condition and table.deletedAt.isNull())
            .limit(1)
            .count() > 0

    /**
     * UPDATE: принимает сущность, применяет body, делает optimistic locking по version.
     * Реальное обновление идёт через DSL, чтобы version участвовала в WHERE.
     */
    override fun update(entity: E, body: E.() -> Unit): E {
        val id = entity.id.value
        val currentVersion = entity.version

        // локально меняем поля
        entity.body()
        entity.updatedAt = Instant.now()

        val updatedRows = table.update({
            table.id eq id and
                    table.deletedAt.isNull() and
                    (table.version eq currentVersion)
        }) { stmt ->
            // перенос конкретных полей в UPDATE
            applyEntityToStatement(entity, stmt)

            stmt[table.updatedAt] = entity.updatedAt
            stmt[table.version] = currentVersion + 1
        }

        if (updatedRows == 0) {
            val exists = table.select(
                table.id eq id and table.deletedAt.isNull()
            ).limit(1).count() > 0

            if (exists) {
                throw Exception(
                    "Entity id=$id was modified by another transaction. Expected version=$currentVersion"
                )
            }
            throw Exception(
                "Entity id=$id not found or already deleted"
            )
        }

        // синхронизируем версию в объекте и возвращаем свежую сущность (можно и entity оставить)
        entity.version = currentVersion + 1
        return entityClass.findById(id)
            ?: error("Entity id=$id must exist after successful update")
    }

    /**
     * Маппинг полей entity -> UpdateStatement.
     * Наследник обязан реализовать копирование своих колонок.
     */
    protected abstract fun applyEntityToStatement(entity: E, stmt: UpdateStatement)

    // SOFT DELETE с проверкой version
    override fun softDelete(entity: E): E {
        val id = entity.id.value
        val currentVersion = entity.version
        val now = Instant.now()

        val updatedRows = table.update({
            table.id eq id and
                    table.deletedAt.isNull() and
                    (table.version eq currentVersion)
        }) { stmt ->
            stmt[table.deletedAt] = now
            stmt[table.updatedAt] = now
            stmt[table.version] = currentVersion + 1
        }

        if (updatedRows == 0) {
            val exists = table.select(
                table.id eq id and table.deletedAt.isNull()
            ).limit(1).count() > 0

            if (exists) {
                throw Exception(
                    "Entity id=$id was modified by another transaction"
                )
            }
            throw Exception(
                "Entity id=$id not found or already deleted"
            )
        }

        return entityClass.findById(id)
            ?: error("Entity id=$id must exist after soft delete")
    }

    // HARD DELETE без version (админская операция)
    override fun hardDelete(id: Long): Boolean =
        table.deleteWhere { table.id eq id } > 0
}

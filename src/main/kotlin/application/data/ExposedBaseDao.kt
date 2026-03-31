package application.data

import application.DatabaseConfig.dbQuery
import extensions.ResultResponse
import extensions.generateMapError
import extensions.printLog
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.dao.LongEntityClass
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.reflect.full.memberProperties
import kotlin.text.toLongOrNull
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toJavaInstant

abstract class ExposedBaseDao<T : BaseTable, E : BaseEntity<*>, S: BaseDTO>(
    protected val table: T,
    protected val entityClass: LongEntityClass<E>
) {

    suspend fun getAll(call: ApplicationCall): ResultResponse {
        return try {
            val data = dbQuery {
                findAll(null)
            }.map { it.toSnapshot() }
            ResultResponse.Success(data)
        } catch (e: Exception) {
            ResultResponse.Error(generateMapError(call, 440 to e.localizedMessage.substringBefore("\n")))
        }
    }

    suspend fun getFromId(call: ApplicationCall): ResultResponse {
        return try {
            val id = call.parameters["id"]?.toLongOrNull() ?: return ResultResponse.Error(
                generateMapError(call, 301 to "Не указан параметр id")
            )
            val users = dbQuery {
                findById(id)?.toSnapshot()
            }
            if (users == null) return ResultResponse.Error(generateMapError(call, 302 to "Не найдена запись с id $id"))
            ResultResponse.Success(users)
        } catch (e: Exception) {
            ResultResponse.Error(generateMapError(call, 440 to e.localizedMessage.substringBefore("\n")))
        }
    }

    suspend fun post(call: ApplicationCall, serializer: KSerializer<List<S>>): ResultResponse {
        return try {
            val multipartData = call.receiveMultipart()
            var jsonString = ""

            multipartData.forEachPart { part ->
                when (part) {
                    is PartData.FormItem -> jsonString = part.value
                    else -> printLog("Unknown part type: ${part::class.simpleName}")
                }
            }

            val dtos = Json.decodeFromString(serializer, jsonString)
            val createdEntities = dbQuery {
                dtos.map { dto ->
                    create {
                        mapDtoToEntity(dto, this)
                    }
                }
            }

            ResultResponse.Success(createdEntities.map { ent -> ent.toSnapshot() })
        } catch (e: Exception) {
            ResultResponse.Error(generateMapError(call, 440 to e.localizedMessage.substringBefore("\n")))
        }
    }

    /********************************/

    protected fun applyEntityToStatement(entity: E, stmt: UpdateStatement) {
        val entityProps = entity::class.memberProperties
        table.columns.forEach { column ->
            val propName = column.name
            val prop = entityProps.find { it.name == propName }
            if (prop != null) {
                val value = prop.getter.call(entity)
                @Suppress("UNCHECKED_CAST")
                stmt.let {
                    when (value) {
                        is String -> it[column as Column<String>] = value
                        is Long -> it[column as Column<Long>] = value
                        is Int -> it[column as Column<Int>] = value
                        is Double -> it[column as Column<Double>] = value
                        is Instant -> it[column as Column<Instant>] = value
                        else -> {}
                    }
                }
            }
        }
    }

    protected abstract fun mapDtoToEntity(dto: S, entity: E)

    // CREATE: возвращает E
    open fun create(body: E.() -> Unit): E {
        val entityResult = entityClass.new {
            body()
        }
        return entityResult
    }

    // READ
    open fun findById(id: Long): E? =
        entityClass.findById(id)?.takeIf { it.deletedAt == null }

    open fun findById(id: EntityID<Long>): E? =
        entityClass.findById(id)?.takeIf { it.deletedAt == null }

    open fun findAll(limit: Int?, withDeleted: Boolean = false): List<E> =
        if (limit != null) {
            entityClass.all()
                .limit(limit)
                .filter { if (withDeleted) it.deletedAt == null else true }
                .toList()
        } else {
            entityClass.all()
                .filter { if (withDeleted) it.deletedAt == null else true }
                .toList()
        }

    open fun findWhere(condition: Op<Boolean>): List<E> =
        entityClass.find { condition and table.deletedAt.isNull() }.toList()

    open fun findOne(condition: Op<Boolean>): E? =
        entityClass.find {
            condition and table.deletedAt.isNull()
        }.firstOrNull()

    open fun count(condition: Op<Boolean>): Long =
        table.select(condition and table.deletedAt.isNull()).count()

    open fun exists(condition: Op<Boolean>): Boolean =
        table.select(condition and table.deletedAt.isNull())
            .limit(1)
            .count() > 0

    /**
     * UPDATE: принимает сущность, применяет body, делает optimistic locking по version.
     * Реальное обновление идёт через DSL, чтобы version участвовала в WHERE.
     */
    open fun update(entity: E, body: E.() -> Unit): E {
        val id = entity.id.value
        val currentVersion = entity.version

        // локально меняем поля
        entity.body()
        entity.updatedAt = Clock.System.now().toJavaInstant()

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

    // SOFT DELETE с проверкой version
    open fun softDelete(entity: E): E {
        val id = entity.id.value
        val currentVersion = entity.version
        val now = Clock.System.now().toJavaInstant()

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
    open fun hardDelete(id: Long): Boolean =
        table.deleteWhere { table.id eq id } > 0
}

package application.data

import application.DatabaseConfig.dbQuery
import extensions.IntBaseDataImpl
import extensions.ResultResponse
import extensions.generateMapError
import io.ktor.server.application.ApplicationCall
import kotlinx.serialization.Serializable
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
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/**
 * Базовый интерфейс для доступа к таблице
 */
interface BaseDao<E : BaseEntity<*>> {

    /**
     * Поиск элемента по id
     * @param id идентфикатор элемента
     * @return найденный элмент или NULL
     */

    fun findById(id: Long): E?
    /**
     * Поиск элемента по id
     * @param id идентфикатор [EntityID] элемента
     * @return найденный элмент или NULL
     */
    fun findById(id: EntityID<Long>): E?

    /**
     * Получение всех элементов таблицы
     * @param limit ограничение получаемых значений из таблицы
     * @return список элементов
     */
    fun findAll(limit: Int? = null): List<E>

    /**
     * Получение всех элементов таблицы по заданному условию
     * @param condition условие выборки
     * @return список элементов
     */
    fun findWhere(condition: Op<Boolean>): List<E>

    /**
     * Получение одного элемента таблицы(первого) по заданному условию
     * @param condition условие выборки
     * @return найденный элмент или NULL
     */
    fun findOne(condition: Op<Boolean>): E?

    /**
     * Получить количество записей из таблицы по элементу
     * @param condition условие выборки
     * @return количество записей, подходящих под условие [condition]
     */
    fun count(condition: Op<Boolean> = Op.TRUE): Long

    /**
     * Проверка существования хотя бы одной строки удволетворяющий условию
     * @param condition условие для проверки
     * @return true если хотя бы одна строка найдена по условию; false во противном случае
     */
    fun exists(condition: Op<Boolean>): Boolean

    /**
     * Создание записи в базе данных
     * @param body тело создаваемого объекта
     * @return созданный экземпляр
     */
    fun create(body: E.() -> Unit): E

    /**
     * Обновление существующей записи в базе данных
     * @param entity объект реализующий [BaseEntity]
     * @param body функция с обновленными параметрами объекта [entity]
     * @return объект после изменения
     */
    fun update(entity: E, body: E.() -> Unit): E

    /**
     * Мягкое удаление объекта (объект остается в базе данных, но ставится флаг удаленности)
     * @param entity объект, который необходимо удалить
     * @return объект, который мы удалили
     */
    fun softDelete(entity: E): E

    /**
     * Строгое удаление объекта из базы данных
     * @param id идентификатор обьекта для удаления
     * @return true если удаление прошло успешно; иначе false
     */
    fun hardDelete(id: Long): Boolean
}

@Serializable
open class BaseDTO(
    /**
     * Дата создания записи
     */
    var _createdAt: Instant = Clock.System.now(),
    /**
     * Дата изменения записи
     */
    var _updatedAt: Instant = Clock.System.now(),
    /**
     * Дата удаления записи
     */
    var _deletedAt: Instant? = null,
    /**
     * Версия записи (для решения проблемы Race Conditions)
     */
    var _version: Long = 0
)

/**
 * Базовый асбтрактный класс таблиц
 * @param name название таблицы
 */
abstract class BaseTable(name: String = "") : LongIdTable(name) {
    /**
     * Колонка даты создания записи (по умолчанию = текущее время)
     */
    val createdAt = timestamp("created_at").default(Clock.System.now().toJavaInstant())
    /**
     * Колонка даты изменения записи (по умолчанию = текущее время)
     */
    val updatedAt = timestamp("updated_at").default(Clock.System.now().toJavaInstant())
    /**
     * Колонка даты удаления записи (по умолчанию null). Если не null - объект считается удаленным (Soft)
     */
    val deletedAt = timestamp("deleted_at").nullable()
    /**
     * Колонка версии записи (для решения проблемы Race Conditions)
     */
    val version = long("version").default(0)
}

abstract class BaseEntity<SNAPSHOT : BaseDTO>(id: EntityID<Long>, table: BaseTable) : LongEntity(id) {
    /**
     * Дата создания записи
     */
    var createdAt by table.createdAt

    /**
     * Дата изменения записи
     */
    var updatedAt by table.updatedAt

    /**
     * Дата удаления записи (по умолчанию null). Если не null - объект считается удаленным (Soft)
     */
    var deletedAt by table.deletedAt

    /**
     * Версия записи (для решения проблемы Race Conditions)
     */
    var version by table.version

    /**
     * Преобразование элемента таблицы к элементу класса
     */
    abstract fun toSnapshot(): SNAPSHOT
}

abstract class ExposedBaseDao<T : BaseTable, E : BaseEntity<*>>(
    protected val table: T,
    protected val entityClass: LongEntityClass<E>
) : BaseDao<E> {

    suspend fun getAll(call: ApplicationCall): ResultResponse {
        return try {
            val data = dbQuery {
                findAll()
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

    /**
     * Маппинг полей entity -> UpdateStatement.
     * Наследник обязан реализовать копирование своих колонок.
     */
    protected abstract fun applyEntityToStatement(entity: E, stmt: UpdateStatement)

    // SOFT DELETE с проверкой version
    override fun softDelete(entity: E): E {
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
    override fun hardDelete(id: Long): Boolean =
        table.deleteWhere { table.id eq id } > 0
}

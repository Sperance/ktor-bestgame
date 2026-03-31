package application.data

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.javatime.timestamp
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toJavaInstant

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
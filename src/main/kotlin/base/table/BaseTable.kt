package base.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.datetime
import java.time.LocalDateTime

/**
 * Все таблицы наследуются отсюда.
 * Гарантирует наличие id, version, created_at, updated_at.
 */
abstract class BaseTable(name: String) : Table(name) {
    val id = long("id").autoIncrement()
    val version = long("version").default(1L)
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }
    val updatedAt = datetime("updated_at").clientDefault { LocalDateTime.now() }

    override val primaryKey = PrimaryKey(id)
}
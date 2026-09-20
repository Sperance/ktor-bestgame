package base.repository

import CONST_FIELD_DELETED
import base.entity.StockEntity
import base.entity.VersionedEntity
import com.mongodb.client.model.Filters
import org.bson.conversions.Bson

/**
 * Правила чтения при мягком удалении.
 *
 * Вынесены отдельно от репозитория, потому что это чистая логика:
 * фильтр собирается без Mongo и проверяется тестами напрямую.
 */
object SoftDelete {

    /**
     * Условие "документ не удалён мягко".
     *
     * Именно `ne(true)`, а не `eq(false)`: поле deleted есть только
     * у [VersionedEntity], документы [StockEntity] его не хранят вовсе,
     * и `eq(false)` отбросил бы их целиком. `ne` отсутствующее поле пропускает.
     */
    val notDeleted: Bson = Filters.ne(CONST_FIELD_DELETED, true)

    /**
     * Фильтр обычного чтения.
     *
     * @param filter Собственный фильтр запроса, null - без фильтра
     * @param includeDeleted true - вернуть и мягко удалённые документы
     */
    fun readFilter(filter: Bson? = null, includeDeleted: Boolean = false): Bson = when {
        includeDeleted -> filter ?: Filters.empty()
        filter == null -> notDeleted
        else -> Filters.and(filter, notDeleted)
    }
}

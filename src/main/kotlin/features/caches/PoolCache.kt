package features.caches

import features.logic.pools.EnumPoolTarget
import features.logic.pools.Pool
import features.logic.pools.PoolRepository
import features.logic.pools.PoolTable

/**
 * Кеш пулов (с 0.56.0): документы `Pool`, сведённые в таблицы по справочникам. Таблица строится
 * один раз на ревизию, а кеши записей строят поверх неё свои индексы тяг, см. [MongoCache.derived].
 */
class PoolCache(repository: PoolRepository) : MongoCache<Pool, PoolRepository>(repository) {
    private val tables = derived { items -> PoolTable.byTarget(items) }

    /** Таблица пулов справочника [target]. */
    fun table(target: EnumPoolTarget): PoolTable = tables.get()[target] ?: PoolTable.EMPTY
}

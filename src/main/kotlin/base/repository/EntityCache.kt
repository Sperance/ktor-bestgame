package base.repository

import base.entity.StockEntity

/**
 * Кеш коллекции, который репозиторий держит в согласии с базой (с 0.49.0):
 * каждая вставка, правка и удаление доезжают сюда после коммита транзакции.
 */
interface EntityCache<T : StockEntity> {
    fun addItem(item: T)
    fun updateItem(item: T)
    fun removeItem(item: T)
}

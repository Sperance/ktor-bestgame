package base.repository

import base.entity.StockEntity

/**
 * Кеш коллекции, который репозиторий держит в согласии с базой (с 0.49.0):
 * каждая вставка, правка и удаление доезжают сюда после коммита транзакции.
 */
interface EntityCache<T : StockEntity> {
    /** Номер снимка: растёт при каждой правке, по нему узнают, что содержимое сменилось. */
    val revision: Long

    fun getCache(): List<T>
    fun findById(id: String): T?

    fun addItem(item: T)
    fun updateItem(item: T)
    fun removeItem(item: T)
}

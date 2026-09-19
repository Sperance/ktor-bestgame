package features.data.items

import base.repository.BaseRepository
import com.mongodb.client.model.Filters
import com.mongodb.kotlin.client.coroutine.ClientSession
import features.caches.ItemsCache
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ItemsRepository : BaseRepository<Items>(entityClass = Items::class), KoinComponent {
    private val itemsCache: ItemsCache by inject()

    init {
        initialize(indexedFields = listOf("category"))
    }

    /**
     * Все предметы категории.
     */
    suspend fun findByCategory(category: String): List<Items> =
        findByFilter(Filters.eq("category", category))

    /**
     * Удаляет все предметы категории в рамках транзакции.
     * Нужен для пересева справочных категорий вроде валюты.
     *
     * @return количество удалённых документов
     */
    suspend fun deleteByCategory(category: String, session: ClientSession): Long =
        collection.deleteMany(session, Filters.eq("category", category)).deletedCount

    override suspend fun validateAfterInsert(entity: Items, session: ClientSession) {
        itemsCache.addItem(entity)
    }

    override suspend fun validateAfterDelete(entity: Items, session: ClientSession, softDelete: Boolean) {
        itemsCache.removeItem(entity)
    }

    override suspend fun validateAfterUpdate(entity: Items, session: ClientSession) {
        itemsCache.updateItem(entity)
    }
}
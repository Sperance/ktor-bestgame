package features.data.items

import base.repository.BaseRepository
import com.mongodb.client.model.Filters
import com.mongodb.kotlin.client.coroutine.ClientSession
import features.caches.ItemsCache
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ItemsRepository : BaseRepository<Items>(entityClass = Items::class), KoinComponent {
    override val cache: ItemsCache by inject()

    init {
        initialize(indexedFields = listOf("category"))
    }

    /**
     * Удаляет все предметы категории в рамках транзакции.
     * Нужен для пересева справочных категорий вроде валюты.
     *
     * @return количество удалённых документов
     */
    suspend fun deleteByCategory(category: String, session: ClientSession): Long =
        collection.deleteMany(session, Filters.eq("category", category)).deletedCount

    /**
     * Удаляет предметы вне [category], которых больше нет в контенте (0.51.0: зелье здоровья).
     *
     * @param itemIds _id всех актуальных предметов
     * @return количество удалённых документов
     */
    suspend fun deleteMissing(itemIds: Collection<String>, category: String, session: ClientSession): Long =
        collection.deleteMany(session, Filters.and(Filters.ne("category", category), Filters.nin("_id", itemIds))).deletedCount
}

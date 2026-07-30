package features.data.items

import base.repository.BaseRepository
import com.mongodb.kotlin.client.coroutine.ClientSession
import features.caches.ItemsCache
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ItemsRepository : BaseRepository<Items>(entityClass = Items::class), KoinComponent {
    private val itemsCache: ItemsCache by inject()

    override suspend fun validateAfterInsert(entity: Items, session: ClientSession) {
        itemsCache.addItemToCache(entity)
    }
}
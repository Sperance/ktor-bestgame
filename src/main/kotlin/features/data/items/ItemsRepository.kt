package features.data.items

import base.repository.BaseRepository
import com.mongodb.kotlin.client.coroutine.ClientSession
import features.caches.ItemsCache

class ItemsRepository : BaseRepository<Items>(entityClass = Items::class) {
    override suspend fun validateAfterInsert(entity: Items, session: ClientSession) {
        ItemsCache.addItemToCache(entity)
    }
}
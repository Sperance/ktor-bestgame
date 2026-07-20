package features.data.enums.items

import base.repository.BaseRepository
import base.repository.UniqueIndexConfig
import com.mongodb.kotlin.client.coroutine.ClientSession

class ItemsRepository : BaseRepository<Items>(
    entityClass = Items::class
) {

    override suspend fun validateAfterInsert(entity: Items, session: ClientSession) {
        ItemsCache.addItemToCache(entity)
    }
}
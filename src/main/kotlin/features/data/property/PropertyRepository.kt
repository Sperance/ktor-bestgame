package features.data.property

import base.repository.BaseRepository
import com.mongodb.kotlin.client.coroutine.ClientSession
import features.caches.PropertyCache

class PropertyRepository : BaseRepository<Property>(entityClass = Property::class) {
    override suspend fun validateAfterInsert(entity: Property, session: ClientSession) {
        PropertyCache.addItemToCache(entity)
    }
}
package features.data.enums.property

import base.repository.BaseRepository
import base.repository.UniqueIndexConfig
import com.mongodb.kotlin.client.coroutine.ClientSession

class PropertyRepository : BaseRepository<Property>(
    entityClass = Property::class
) {
    override suspend fun validateAfterInsert(entity: Property, session: ClientSession) {
        PropertyCache.addItemToCache(entity)
    }
}
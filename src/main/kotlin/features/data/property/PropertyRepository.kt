package features.data.property

import base.repository.BaseRepository
import base.repository.UniqueIndexConfig
import com.mongodb.kotlin.client.coroutine.ClientSession

class PropertyRepository : BaseRepository<Property>(
    entityClass = Property::class
) {
    init {
        initialize(uniqueIndexes = listOf(
            UniqueIndexConfig(
                indexName = "idx_unique_code",
                fields = listOf("code")
            )
        ))
    }

    override suspend fun validateAfterInsert(entity: Property, session: ClientSession) {
        PropertyCache.addItemToCache(entity)
    }
}
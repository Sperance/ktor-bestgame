package features.data.items

import base.repository.BaseRepository
import base.repository.UniqueIndexConfig
import com.mongodb.kotlin.client.coroutine.ClientSession

class ItemsRepository : BaseRepository<Items>(
    entityClass = Items::class
) {
    init {
        initialize(uniqueIndexes = listOf(
            UniqueIndexConfig(
                indexName = "idx_unique_name",
                fields = listOf("name")
            )
        ))
    }

    override suspend fun validateAfterInsert(entity: Items, session: ClientSession) {
        ItemsCache.addItemToCache(entity)
    }
}
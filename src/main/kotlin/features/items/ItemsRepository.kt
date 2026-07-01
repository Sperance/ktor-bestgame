package features.items

import base.repository.BaseRepository
import base.repository.UniqueIndexConfig

object ItemsRepository : BaseRepository<Items>(
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
}
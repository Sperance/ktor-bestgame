package features.property

import base.repository.BaseRepository
import base.repository.UniqueIndexConfig

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
}
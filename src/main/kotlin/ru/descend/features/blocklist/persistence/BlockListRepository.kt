package ru.descend.features.blocklist.persistence

import ru.descend.features.blocklist.model.BlockList
import ru.descend.infrastructure.mongo.BaseRepository
import com.mongodb.kotlin.client.coroutine.ClientSession
import ru.descend.infrastructure.cache.BlockListCache
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BlockListRepository : BaseRepository<BlockList>(entityClass = BlockList::class), KoinComponent {
    private val blockListCache: BlockListCache by inject()

    override suspend fun validateAfterInsert(entity: BlockList, session: ClientSession) {
        blockListCache.addItem(entity)
    }

    override suspend fun validateAfterDelete(entity: BlockList, session: ClientSession, softDelete: Boolean) {
        blockListCache.removeItem(entity)
    }

    override suspend fun validateAfterUpdate(entity: BlockList, session: ClientSession) {
        blockListCache.updateItem(entity)
    }
}

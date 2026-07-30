package features.data.blockList

import base.repository.BaseRepository
import com.mongodb.kotlin.client.coroutine.ClientSession
import features.caches.BlockListCache
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BlockListRepository : BaseRepository<BlockList>(entityClass = BlockList::class), KoinComponent {
    private val blockListCache: BlockListCache by inject()

    override suspend fun validateAfterInsert(entity: BlockList, session: ClientSession) {
        blockListCache.addItemToCache(entity)
    }
}
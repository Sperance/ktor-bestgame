package features.data.blockList

import base.repository.BaseRepository
import com.mongodb.kotlin.client.coroutine.ClientSession
import features.caches.BlockListCache

class BlockListRepository : BaseRepository<BlockList>(entityClass = BlockList::class) {
    override suspend fun validateAfterInsert(entity: BlockList, session: ClientSession) {
        BlockListCache.addItemToCache(entity)
    }
}
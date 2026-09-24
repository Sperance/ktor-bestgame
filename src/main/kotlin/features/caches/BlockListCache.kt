package features.caches

import features.data.blockList.BlockList
import features.data.blockList.BlockListRepository

class BlockListCache(repository: BlockListRepository) : MongoCache<BlockList, BlockListRepository>(repository) {
    private val addresses = derived { items -> items.mapTo(HashSet()) { it.address } }

    fun isBlocked(address: String): Boolean = address in addresses.get()
}

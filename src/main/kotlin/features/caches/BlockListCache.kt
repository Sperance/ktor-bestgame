package features.caches

import features.data.blockList.BlockList
import features.data.blockList.BlockListRepository

class BlockListCache(repository: BlockListRepository) : MongoCache<BlockList, BlockListRepository>(repository)
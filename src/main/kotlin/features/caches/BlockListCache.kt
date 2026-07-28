package features.caches

import features.data.blockList.BlockList
import features.data.blockList.BlockListRepository

object BlockListCache : MongoCache<BlockList, BlockListRepository>()
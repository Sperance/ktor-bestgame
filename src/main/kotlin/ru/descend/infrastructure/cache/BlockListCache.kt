package ru.descend.infrastructure.cache

import ru.descend.features.blocklist.model.BlockList
import ru.descend.features.blocklist.persistence.BlockListRepository

class BlockListCache(repository: BlockListRepository) : MongoCache<BlockList, BlockListRepository>(repository)

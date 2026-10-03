package ru.descend.exileforge.features.data.blockList

import ru.descend.exileforge.base.repository.BaseRepository
import ru.descend.exileforge.base.repository.EntityCache
import ru.descend.exileforge.features.caches.BlockListCache

/** Блокировки: справочная коллекция, её кеш [BlockListCache] подключается при своём создании. */
class BlockListRepository : BaseRepository<BlockList>(entityClass = BlockList::class) {
    public override var cache: EntityCache<BlockList>? = null
}

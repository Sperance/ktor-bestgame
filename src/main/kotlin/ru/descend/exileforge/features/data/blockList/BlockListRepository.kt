package ru.descend.exileforge.features.data.blockList
import com.mongodb.kotlin.client.coroutine.ClientSession
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import ru.descend.exileforge.base.repository.BaseRepository
import ru.descend.exileforge.features.caches.BlockListCache

class BlockListRepository :
    BaseRepository<BlockList>(entityClass = BlockList::class),
    KoinComponent {
    override val cache: BlockListCache by inject()
}

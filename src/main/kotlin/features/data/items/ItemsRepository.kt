package features.data.items

import base.repository.BaseRepository
import com.mongodb.kotlin.client.coroutine.ClientSession
import features.caches.ItemsCache
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ItemsRepository : BaseRepository<Items>(entityClass = Items::class), KoinComponent {
    private val itemsCache: ItemsCache by inject()

    override suspend fun validateAfterInsert(entity: Items, session: ClientSession) {
        itemsCache.addItem(entity)
    }

    override suspend fun validateAfterDelete(entity: Items, session: ClientSession, softDelete: Boolean) {
        itemsCache.removeItem(entity)
    }

    override suspend fun validateAfterUpdate(entity: Items, session: ClientSession) {
        itemsCache.updateItem(entity)
    }
}
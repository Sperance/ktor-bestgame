package features.data.property

import base.repository.BaseRepository
import com.mongodb.kotlin.client.coroutine.ClientSession
import features.caches.PropertyCache
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class PropertyRepository : BaseRepository<Property>(entityClass = Property::class), KoinComponent {
    private val propertyCache: PropertyCache by inject()

    override suspend fun validateAfterInsert(entity: Property, session: ClientSession) {
        propertyCache.addItem(entity)
    }

    override suspend fun validateAfterDelete(entity: Property, session: ClientSession, softDelete: Boolean) {
        propertyCache.removeItem(entity)
    }

    override suspend fun validateAfterUpdate(entity: Property, session: ClientSession) {
        propertyCache.updateItem(entity)
    }
}
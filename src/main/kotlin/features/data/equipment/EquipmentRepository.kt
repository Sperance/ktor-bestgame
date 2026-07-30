package features.data.equipment

import base.repository.BaseRepository
import com.mongodb.kotlin.client.coroutine.ClientSession
import features.caches.EquipmentCache
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class EquipmentRepository : BaseRepository<Equipment>(entityClass = Equipment::class), KoinComponent {
    private val equipmentCache: EquipmentCache by inject()

    override suspend fun validateAfterInsert(entity: Equipment, session: ClientSession) {
        equipmentCache.addItemToCache(entity)
    }
}
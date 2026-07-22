package features.data.equipment

import base.repository.BaseRepository
import com.mongodb.kotlin.client.coroutine.ClientSession
import features.caches.EquipmentCache

class EquipmentRepository : BaseRepository<Equipment>(entityClass = Equipment::class) {
    override suspend fun validateAfterInsert(entity: Equipment, session: ClientSession) {
        EquipmentCache.addItemToCache(entity)
    }
}
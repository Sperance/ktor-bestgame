package features.data.equipmentName

import base.repository.BaseRepository
import com.mongodb.kotlin.client.coroutine.ClientSession
import features.caches.EquipmentNameCache

class EquipmentNameRepository : BaseRepository<EquipmentName>(entityClass = EquipmentName::class) {
    override suspend fun validateAfterInsert(entity: EquipmentName, session: ClientSession) {
        EquipmentNameCache.addItemToCache(entity)
    }
}
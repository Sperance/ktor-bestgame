package features.data.enums.equipment

import base.repository.BaseRepository
import com.mongodb.kotlin.client.coroutine.ClientSession

class EquipmentRepository : BaseRepository<Equipment>(
    entityClass = Equipment::class
) {
    override suspend fun validateAfterInsert(entity: Equipment, session: ClientSession) {
        EquipmentCache.addItemToCache(entity)
    }
}
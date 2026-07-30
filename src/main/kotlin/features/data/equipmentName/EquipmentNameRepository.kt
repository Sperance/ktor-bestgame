package features.data.equipmentName

import base.repository.BaseRepository
import com.mongodb.kotlin.client.coroutine.ClientSession
import features.caches.EquipmentNameCache
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class EquipmentNameRepository : BaseRepository<EquipmentName>(entityClass = EquipmentName::class), KoinComponent {
    private val equipmentNameCache: EquipmentNameCache by inject()

    override suspend fun validateAfterInsert(entity: EquipmentName, session: ClientSession) {
        equipmentNameCache.addItemToCache(entity)
    }
}
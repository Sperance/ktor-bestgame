package features.data.equipment

import base.repository.BaseRepository
import com.mongodb.kotlin.client.coroutine.ClientSession
import features.caches.EquipmentCache
import features.data.equipment.equipment_data.Equipment
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class EquipmentRepository : BaseRepository<Equipment>(entityClass = Equipment::class), KoinComponent {
    override val cache: EquipmentCache by inject()

}
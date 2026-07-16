package features.data.equipment

import base.repository.BaseRepository

class EquipmentRepository : BaseRepository<Equipment>(
    entityClass = Equipment::class
) {
    init {
        initialize()
    }
}
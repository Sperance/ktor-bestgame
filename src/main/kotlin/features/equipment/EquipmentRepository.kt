package features.equipment

import application.enums.EnumEquipmentType
import base.repository.BaseRepository

class EquipmentRepository : BaseRepository<Equipment>(
    entityClass = Equipment::class
) {
    init {
        initialize()
    }

    override suspend fun validateBeforeInsert(entity: Equipment) {
        if (entity.slot == EnumEquipmentType.UNDEFINED) throw EquipmentExceptions.funExceptionType("validateBeforeInsert")
    }
}
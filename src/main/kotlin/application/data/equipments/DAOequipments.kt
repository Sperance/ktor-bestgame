package application.data.equipments

import application.data.ExposedBaseDao
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class DAOequipments : ExposedBaseDao<EquipmentsTable, EquipmentEntity, SnapshotEquipment>(
    EquipmentsTable,
    EquipmentEntity.Companion
) {
    override fun mapDtoToEntity(dto: SnapshotEquipment, entity: EquipmentEntity) {
        entity.name = dto._name
        entity.content = dto._content
        entity.uuid = dto._uuid
        entity.enumEquipmentType = dto._enumEquipmentType
        entity.requirements = dto._requirements
        entity.params = dto._params
        entity.buffs = dto._buffs
        entity.bools = dto._bools
    }
}
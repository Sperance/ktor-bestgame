package application.data.equipments

import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import application.data.ExposedBaseDao
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class DAOequipments : ExposedBaseDao<EquipmentsTable, EquipmentEntity>(
    EquipmentsTable,
    EquipmentEntity.Companion
) {
    override fun applyEntityToStatement(entity: EquipmentEntity, stmt: UpdateStatement) {
        stmt[table.character] = entity.character.id
        stmt[table.name] = entity.name
        stmt[table.content] = entity.content
        stmt[table.uuid] = entity.uuid
        stmt[table.enumEquipmentType] = entity.enumEquipmentType
        stmt[table.requirements] = entity.requirements
        stmt[table.params] = entity.params
        stmt[table.buffs] = entity.buffs
        stmt[table.bools] = entity.bools
    }
}
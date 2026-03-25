package application.data.equipments

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.LongEntityClass
import org.jetbrains.exposed.v1.json.jsonb
import application.data.characters.CharacterEntity
import application.data.characters.CharactersTable
import application.data.BaseEntity
import application.data.BaseTable
import application.model.enums.EnumEquipmentType
import application.model.ParamsStock
import application.model.Stat
import application.model.StatBool
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
object EquipmentsTable : BaseTable("equipments") {
    val character = reference("character", CharactersTable)
    val name = varchar("name", 255)
    val content = text("content")
    val uuid = uuid("uuid").uniqueIndex().clientDefault { Uuid.random() }
    val enumEquipmentType = enumeration("enum_equipment_type", EnumEquipmentType::class)

    val requirements = jsonb<MutableSet<Stat>>(
        name = "requirements",
        jsonConfig = Json
    ).nullable()

    val params = jsonb<MutableSet<ParamsStock>>(
        name = "params",
        jsonConfig = Json
    ).nullable()

    val buffs = jsonb<MutableSet<Stat>>(
        name = "buffs",
        jsonConfig = Json
    ).nullable()

    val bools = jsonb<MutableSet<StatBool>>(
        name = "bools",
        jsonConfig = Json
    ).nullable()
}

@OptIn(ExperimentalUuidApi::class)
class EquipmentEntity(id: EntityID<Long>) : BaseEntity<SnapshotEquipment>(id, EquipmentsTable) {
    var character by CharacterEntity referencedOn EquipmentsTable.character
    var name by EquipmentsTable.name
    var content by EquipmentsTable.content
    var uuid by EquipmentsTable.uuid
    var enumEquipmentType by EquipmentsTable.enumEquipmentType

    var requirements by EquipmentsTable.requirements
    var params by EquipmentsTable.params
    var buffs by EquipmentsTable.buffs
    var bools by EquipmentsTable.bools

    override fun toSnapshot(): SnapshotEquipment =
        SnapshotEquipment(
            _id = id.value,
            _name = name,
            _content = content,
            _uuid = uuid,
            _enumEquipmentType = enumEquipmentType,
            _requirements = requirements,
            _params = params,
            _buffs = buffs,
            _bools = bools
        )

    override fun toString(): String {
        return "EquipmentEntity(character=${character.id}, name='$name', content='$content', uuid=$uuid, requirements=$requirements, params=$params, buffs=$buffs, bools=$bools)"
    }

    companion object : LongEntityClass<EquipmentEntity>(EquipmentsTable)
}
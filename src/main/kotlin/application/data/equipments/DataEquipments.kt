package application.data.equipments

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.LongEntityClass
import org.jetbrains.exposed.v1.json.jsonb
import application.data.characters.CharacterEntity
import application.data.characters.CharactersTable
import application.data.BaseEntity
import application.data.BaseTable
import application.enums.EnumEquipmentType
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

/**
 * Основная сущность Экипировка персонажа (связь один [CharacterEntity] к одному [EquipmentEntity])
 */
@OptIn(ExperimentalUuidApi::class)
class EquipmentEntity(id: EntityID<Long>) : BaseEntity<SnapshotEquipment>(id, EquipmentsTable) {
    /**
     * Ссылка на персонажа [CharacterEntity], владельца экипировки
     */
    var character by CharacterEntity referencedOn EquipmentsTable.character

    /**
     * Название экипировки
     */
    var name by EquipmentsTable.name

    /**
     * Комментарий экипировки
     */
    var content by EquipmentsTable.content

    /**
     * Уникальный идентификатор экипировки
     */
    var uuid by EquipmentsTable.uuid

    /**
     * Тип экипировки [EnumEquipmentType]
     */
    var enumEquipmentType by EquipmentsTable.enumEquipmentType

    /**
     * Список параметров [Stat] для возможности использования экипировки
     */
    var requirements by EquipmentsTable.requirements

    /**
     * Список [ParamsStock] параметров экипировки
     */
    var params by EquipmentsTable.params

    /**
     * Список [Stat] бонусов экипировки
     */
    var buffs by EquipmentsTable.buffs

    /**
     * Список [StatBool] флагов экипировки
     */
    var bools by EquipmentsTable.bools

    /**
     * Конвертация объекта [EquipmentEntity] в объект [SnapshotEquipment]
     * Изменения [CharacterEntity] меняют базу данных напрямую, изменения [SnapshotEquipment] изменяют только объект класса
     * @return экземпляр класса [SnapshotEquipment], который можно изменять без сохранений в БД
     */
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

    /**
     * Строковое представление объекта [EquipmentEntity]
     */
    override fun toString(): String {
        return "EquipmentEntity(character=${character.id}, name='$name', content='$content', uuid=$uuid, requirements=$requirements, params=$params, buffs=$buffs, bools=$bools)"
    }

    companion object : LongEntityClass<EquipmentEntity>(EquipmentsTable)
}
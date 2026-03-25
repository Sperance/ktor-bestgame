package application.data.characters

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.LongEntityClass
import org.jetbrains.exposed.v1.json.jsonb
import application.data.BaseEntity
import application.data.BaseTable
import application.data.equipments.EquipmentEntity
import application.data.equipments.EquipmentsTable
import application.data.inventory.InventoryEntity
import application.data.inventory.InventoryTable
import application.model.ParamsStock
import application.model.Stat
import application.model.StatBool
import application.model.enums.EnumStatBool
import application.model.enums.EnumStatKey
import extensions.ResultExec
import application.data.users.UserEntity
import application.data.users.UsersTable

object CharactersTable : BaseTable("characters") {
    val user = reference("user_id", UsersTable)
    val name = varchar("name", 32)

    val level = short("level").default(1)
    val experience = integer("experience").default(0)

    val params = jsonb<MutableSet<ParamsStock>>(
        name = "params",
        jsonConfig = Json
    )

    val buffs = jsonb<MutableSet<Stat>>(
        name = "buffs",
        jsonConfig = Json
    ).nullable()

    val bools = jsonb<MutableSet<StatBool>>(
        name = "bools",
        jsonConfig = Json
    ).nullable()
}

class CharacterEntity(id: EntityID<Long>) : BaseEntity<SnapshotCharacter>(id, CharactersTable) {
    var user by UserEntity referencedOn CharactersTable.user
    var name by CharactersTable.name
    var level by CharactersTable.level
    var experience by CharactersTable.experience
    var params by CharactersTable.params
    var buffs by CharactersTable.buffs
    var bools by CharactersTable.bools

    val inventory by InventoryEntity referrersOn InventoryTable.character
    private val equipments by EquipmentEntity referrersOn EquipmentsTable.character

    override fun toSnapshot(): SnapshotCharacter =
        SnapshotCharacter(
            _id = id.value,
            _name = name,
            _level = level,
            _experience = experience,
            _params = params,
            _buffs = buffs,
            _bools = bools,
            _inventory = getInventory().toSnapshot(),
            _equipments = getEquipments(),
            _userId = user.id.value
        ).apply {
            _createdAt = createdAt
            _updatedAt = updatedAt
            _deletedAt = deletedAt
            _version = version
        }

    fun getInventory() = inventory.first()

    fun getEquipments() = equipments.toList()

    fun getStockParams(): MutableSet<ParamsStock> {
        val stock = mutableSetOf<ParamsStock>()
        stock.add(ParamsStock(EnumStatKey.LIFE, 100.0))
        stock.add(ParamsStock(EnumStatKey.STR, 1.0))
        stock.add(ParamsStock(EnumStatKey.DEX, 1.0))
        stock.add(ParamsStock(EnumStatKey.INT, 1.0))
        stock.add(ParamsStock(EnumStatKey.INVENTORY_SIZE, 10.0))
        stock.add(ParamsStock(EnumStatKey.CRIT_DAMAGE, 200.0))
        stock.add(ParamsStock(EnumStatKey.ATTACK_SPEED, 1.5))
        return stock
    }

    /**
     * Получение ссылки на параметр по ключу
     * @param enum ключ параметра как Enum
     */
    fun getStat(enum: EnumStatBool): StatBool? {
        return bools?.firstOrNull { it.key == enum }
    }

    /**
     * Установка значения параметра (или добавления, если параметра нет)
     * @param value нужный параметр
     */
    fun setStat(value: StatBool): ResultExec {
        val currentItems = bools?.toMutableSet() ?: mutableSetOf()

        //Если параметр уже есть - обновляем, если нет - добавляем
        currentItems.find { it.key == value.key }?.
        let { tt ->
            tt.value = value.value
        }?:currentItems.add(value)

        bools = currentItems
        return ResultExec.Success()
    }

    /*****/

    /**
     * Получение ссылки на параметр по ключу
     * @param enum ключ параметра как Enum
     */
    fun getStat(enum: EnumStatKey): ParamsStock? {
        return params.firstOrNull { it.param == enum }
    }

    /**
     * Установка значения параметра (или добавления, если параметра нет)
     * @param value нужный параметр
     * @param minVal итоговое значение параметра не сможет быть ниже указанного
     * @param maxVal итоговое значение параметра не сможет быть выше указанного
     */
    fun setStat(value: ParamsStock, minVal: Double? = null, maxVal: Double? = null): ResultExec {
        if (minVal != null && maxVal != null && minVal > maxVal) throw Exception("В методе setStat параметр minVal не может быть больше, чем параметр maxVal")

        val currentItems = params.toMutableSet()

        //Если параметр уже есть - обновляем, если нет - добавляем
        currentItems.find { it.param == value.param }?.
        let { tt ->
            tt.value = value.value
            if (minVal != null && tt.value < minVal) tt.value = minVal
            if (maxVal != null && tt.value > maxVal) tt.value = maxVal
        }?:currentItems.add(value)

        params = currentItems
        return ResultExec.Success()
    }

    /**
     * Добавление параметра или увеличение значения параметра
     * @param value необходимый параметр для модификации
     * @param maxVal итоговое значение параметра не сможет быть выше указанного
     */
    fun addStat(value: ParamsStock, maxVal: Double? = null): ResultExec {
        val currentItems = params.toMutableSet()

        currentItems.find { it.param == value.param }?.
        let { tt ->
            tt.value += value.value
            if (maxVal != null && tt.value > maxVal) tt.value = maxVal
        }?:currentItems.add(value)

        params = currentItems
        return ResultExec.Success()
    }

    /**
     * Уменьшение значения параметра
     * @param value элемент для уменьшения с количеством
     * @param minVal итоговое значение параметра не сможет быть ниже указанного
     */
    fun remStat(value: ParamsStock, minVal: Double? = null): ResultExec {

        //Находим ссылку на нужный параметр
        getStat(value.param)?.let { param ->
            //Уменьшаем значение параметра
            if (param.value > value.value) {
                param.value -= value.value
            } else {
                //Параметр не должен быть меньше нуля (пока) и не должен быть полностью удален (всегда)
                param.value = 0.0
            }

            if (minVal != null && param.value < minVal) param.value = minVal
        }

        return ResultExec.Success()
    }

    override fun toString(): String {
        return "CharacterEntity(user=$user, name='$name', params=$params, buffs=$buffs)"
    }

    companion object : LongEntityClass<CharacterEntity>(CharactersTable)
}
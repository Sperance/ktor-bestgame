package application.data.characters

import application.data.BaseDTO
import application.data.equipments.EquipmentEntity
import application.data.inventory.SnapshotInventory
import application.model.ParamsStock
import application.model.Stat
import application.model.StatBool
import application.model.enums.EnumStatType
import extensions.addPercent

class SnapshotCharacter(
    /**
     * id персонажа
     */
    val _id: Long,
    /**
     * Имя персонажа
     */
    var _name: String,
    /**
     * Уровень персонажа
     */
    var _level: Short,
    /**
     * Текущий опыт персонажа
     */
    var _experience: Int,
    /**
     * Все параметры персонажа
     */
    var _params: MutableSet<ParamsStock>,
    /**
     * Все дополнительные бонусы к параметрам персонажа
     */
    var _buffs: MutableSet<Stat>?,
    /**
     * Все флаги персонажа
     */
    var _bools: MutableSet<StatBool>?,
    /**
     * Объект класса [SnapshotInventory] - инвентарь персонажа (без экипировки)
     */
    var _inventory: SnapshotInventory,
    /**
     * [List] объектов [EquipmentEntity] - вся экипировка персонажа
     */
    var _equipments: List<EquipmentEntity>?,
    /**
     * id [application.data.users.UserEntity] владельца персонажа
     */
    val _userId: Long
) : BaseDTO() {

    /**
     * Метод для расчетов параметров с учетом всех дополнительных модификаторов
     * @return Список элементов [ParamsStock]
     */
    fun calculateParamsWithBuffs(): MutableSet<ParamsStock> {
        val resultSet = _params.map { it.copy() }.toMutableSet()

        _buffs?.let { buffs ->
            buffs.forEach { buf ->
                resultSet.find { it.param.code == buf.key.code }?.let { par ->
                    when (buf.type) {
                        EnumStatType.FLAT -> par.value += buf.value
                        EnumStatType.PERCENT -> par.value = par.value.addPercent(buf.value)
                    }
                }
            }
        }

        return resultSet
    }

    /**
     * Строковое представление объекта [SnapshotCharacter]
     */
    override fun toString(): String {
        return "SnapshotCharacter(_id=$_id, _name='$_name', _level=$_level, _experience=$_experience, _params=$_params, _buffs=$_buffs, _bools=$_bools, _userId=$_userId)"
    }
}
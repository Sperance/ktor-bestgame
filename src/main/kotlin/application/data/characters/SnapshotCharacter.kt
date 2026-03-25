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
    val _id: Long,
    var _name: String,
    var _level: Short,
    var _experience: Int,
    var _params: MutableSet<ParamsStock>,
    var _buffs: MutableSet<Stat>?,
    var _bools: MutableSet<StatBool>?,
    var _inventory: SnapshotInventory,
    var _equipments: List<EquipmentEntity>?,
    val _userId: Long
) : BaseDTO() {

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

    override fun toString(): String {
        return "SnapshotCharacter(_id=$_id, _name='$_name', _level=$_level, _experience=$_experience, _params=$_params, _buffs=$_buffs, _bools=$_bools, _userId=$_userId)"
    }
}
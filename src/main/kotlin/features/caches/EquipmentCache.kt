package features.caches

import application.enums.EnumEquipmentType
import features.data.equipment.EquipmentRepository
import features.data.equipment.equipment_data.Equipment

class EquipmentCache(repository: EquipmentRepository) : MongoCache<Equipment, EquipmentRepository>(repository) {
    private val byCode = derived { items -> items.associateBy { it.code } }
    private val bySlot = derived { items -> items.groupBy { it.slot } }

    fun findByCode(code: String): Equipment? = byCode.get()[code]

    /** Шаблоны слота, в порядке кеша. */
    fun findBySlot(slot: EnumEquipmentType): List<Equipment> = bySlot.get()[slot].orEmpty()
}

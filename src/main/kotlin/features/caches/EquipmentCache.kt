package features.caches

import application.enums.EnumEquipmentType
import features.data.equipment.EquipmentRepository
import features.data.equipment.equipment_data.Equipment
import features.logic.pools.Pools
import features.logic.pools.Weighted
import java.util.concurrent.ConcurrentHashMap

class EquipmentCache(repository: EquipmentRepository) : MongoCache<Equipment, EquipmentRepository>(repository) {
    private val byCode = uniqueIndex { it.code }
    private val bySlot = groupIndex { it.slot }
    private val pools = derived { items -> Pools.Index(items) }
    private val leveled = derived { _ -> ConcurrentHashMap<List<String>, Leveled>() }

    /** Пул по возрастанию требуемого уровня: срез «до уровня» - двоичный поиск, а не фильтр. */
    private class Leveled(pool: List<Weighted<Equipment>>) {
        private val sorted = pool.sortedBy { it.value.requiredLevel }
        private val levels = IntArray(sorted.size) { sorted[it].value.requiredLevel }

        fun upTo(level: Int): List<Weighted<Equipment>> {
            var low = 0
            var high = levels.size
            while (low < high) {
                val middle = (low + high) ushr 1
                if (levels[middle] <= level) low = middle + 1 else high = middle
            }
            return sorted.subList(0, low)
        }
    }

    fun findByCode(code: String): Equipment? = byCode.get()[code]

    /** Шаблоны слота, в порядке кеша. */
    fun findBySlot(slot: EnumEquipmentType): List<Equipment> = bySlot.get()[slot].orEmpty()

    /** Шаблоны пулов [tags] с их весами, см. [Pools.of]; собирается один раз на ревизию. */
    fun pool(tags: List<String>): List<Weighted<Equipment>> = pools.get().of(tags)

    /** Те же шаблоны, которые по уровню доступны на [level]: что падает на локации этого уровня. */
    fun poolUpTo(tags: List<String>, level: Int): List<Weighted<Equipment>> =
        leveled.get().getOrPut(tags) { Leveled(pool(tags)) }.upTo(level)
}

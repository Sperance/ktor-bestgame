package features.caches

import extensions.RandomExt
import features.logic.modifiers.ModifierDefinition
import features.logic.modifiers.ModifierDefinitionRepository
import features.logic.modifiers.ModifierTier
import features.logic.pools.EnumPoolTarget
import features.logic.pools.Weighted
import kotlin.random.Random

class ModifierDefinitionCache(
    repository: ModifierDefinitionRepository,
    private val poolCache: PoolCache,
) : MongoCache<ModifierDefinition, ModifierDefinitionRepository>(repository) {

    /**
     * Тиры одного модификатора под вопрос «какой выпал на этом уровне»: по порогу уровня
     * с накопленными весами. Ролл - два двоичных поиска, без фильтра и сортировки на каждый вызов.
     * Вес тира - его [ModifierTier.weight] (0.66.0), как spawn weight в POE; тир без веса
     * весит свой номер: чем лучше тир, тем он реже.
     */
    private class Ladder(tiers: List<ModifierTier>) {
        private val byLevel: List<Pair<Int, ModifierTier>> = tiers.mapIndexed { index, tier -> index + 1 to tier }.sortedBy { it.second.level }
        private val levels = IntArray(byLevel.size) { byLevel[it].second.level }
        private val cumulative = LongArray(byLevel.size).also { sums ->
            var total = 0L
            byLevel.forEachIndexed { index, (number, tier) -> total += tier.weight.takeIf { it > 0 } ?: number; sums[index] = total }
        }

        fun roll(itemLevel: Int, random: Random): Pair<Int, ModifierTier>? {
            if (byLevel.isEmpty()) return null
            val open = openCount(itemLevel)
            // Ни один тир не открыт по уровню - самый слабый, чтобы предмет всё равно получил модификатор.
            if (open == 0) return byLevel.maxBy { it.first }
            val point = random.nextLong(cumulative[open - 1])
            var low = 0
            var high = open - 1
            while (low < high) {
                val middle = (low + high) ushr 1
                if (cumulative[middle] > point) high = middle else low = middle + 1
            }
            return byLevel[low]
        }

        /** Сколько тиров открыто на уровне: первая позиция с порогом выше уровня. */
        private fun openCount(itemLevel: Int): Int {
            var low = 0
            var high = levels.size
            while (low < high) {
                val middle = (low + high) ushr 1
                if (levels[middle] <= itemLevel) low = middle + 1 else high = middle
            }
            return low
        }
    }

    private val byCode = uniqueIndex { it.code }
    private val monsters = derived { items -> items.filter { it.isMonster() } }
    private val ladders = derived { items -> items.associate { it.code to Ladder(it.tiers) } }
    private val pools = derived(poolCache) { items -> poolCache.table(EnumPoolTarget.MODIFIER).index(items) }
    private val affixPools = derived(poolCache) { items ->
        // Описание без тиров не роллится: выбранное, оно молча съело бы место аффикса (0.53.0)
        poolCache.table(EnumPoolTarget.MODIFIER).index(items.filter { it.isAffix() && !it.crafted && it.tiers.isNotEmpty() })
    }

    /**
     * Поиск описания модификатора по стабильному коду.
     */
    fun findByCode(code: String): ModifierDefinition? = byCode.get()[code]

    /** Описания по списку кодов, в порядке кодов; неизвестные пропускаются. */
    fun findAllByCode(codes: Collection<String>): List<ModifierDefinition> {
        val index = byCode.get()
        return codes.mapNotNull { index[it] }
    }

    /** Описания модификаторов монстров (0.66.0), один список на ревизию: по нему кампания собирает карты. */
    fun monsters(): List<ModifierDefinition> = monsters.get()

    /** Модификаторы пулов [tags] с их весами; собирается один раз на ревизию описаний и пулов. */
    fun pool(tags: List<String>): List<Weighted<ModifierDefinition>> = pools.get().of(tags)

    /** То же, только аффиксы с тирами и не ремесленные - всё, что катают сферы. */
    fun affixPool(tags: List<String>): List<Weighted<ModifierDefinition>> = affixPools.get().of(tags)

    /**
     * Взвешенный ролл тира среди доступных на данном item level: номер тира и сам тир.
     *
     * Если ни один тир не открыт по item level - берётся самый слабый,
     * чтобы предмет низкого уровня всё равно получил модификатор.
     */
    fun rollTier(code: String, itemLevel: Int, random: Random = RandomExt.random): Pair<Int, ModifierTier>? =
        ladders.get()[code]?.roll(itemLevel, random)
}

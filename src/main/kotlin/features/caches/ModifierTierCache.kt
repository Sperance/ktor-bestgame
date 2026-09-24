package features.caches

import extensions.RandomExt
import features.logic.modifiers.ModifierTier
import features.logic.modifiers.ModifierTierRepository
import kotlin.random.Random

class ModifierTierCache(
    repository: ModifierTierRepository
) : MongoCache<ModifierTier, ModifierTierRepository>(repository) {

    /**
     * Тиры одного модификатора, разложенные под оба вопроса кеша: «какие тиры есть»
     * (по номеру) и «какой выпал на этом уровне» (по порогу уровня с накопленными весами).
     * Ролл - два двоичных поиска, без фильтра и сортировки на каждый вызов.
     */
    private class Ladder(tiers: List<ModifierTier>) {
        val byTier: List<ModifierTier> = tiers.sortedBy { it.tier }
        val byNumber: Map<Int, ModifierTier> = byTier.associateBy { it.tier }
        private val byLevel: List<ModifierTier> = tiers.sortedBy { it.minItemLevel }
        private val levels = IntArray(byLevel.size) { byLevel[it].minItemLevel }
        private val cumulative = LongArray(byLevel.size).also { sums ->
            var total = 0L
            byLevel.forEachIndexed { index, tier -> total += tier.weight.coerceAtLeast(0); sums[index] = total }
        }

        fun roll(itemLevel: Int, random: Random): ModifierTier {
            val open = openCount(itemLevel)
            // Ни один тир не открыт по уровню - самый слабый, чтобы предмет всё равно получил модификатор.
            if (open == 0) return byTier.last()
            val total = cumulative[open - 1]
            if (total == 0L) return byLevel.subList(0, open).maxBy { it.tier }
            val point = random.nextLong(total)
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

    private val ladders = derived { items -> items.groupBy { it.modifierId }.mapValues { (_, tiers) -> Ladder(tiers) } }

    /**
     * Все тиры модификатора: от лучшего (тир 1) к худшему.
     */
    fun findByModifier(modifierId: String): List<ModifierTier> = ladders.get()[modifierId]?.byTier.orEmpty()

    /** Тир модификатора по номеру. */
    fun findTier(modifierId: String, tier: Int): ModifierTier? = ladders.get()[modifierId]?.byNumber?.get(tier)

    /**
     * Взвешенный ролл тира среди доступных на данном item level.
     *
     * Если ни один тир не открыт по item level - берётся самый слабый,
     * чтобы предмет низкого уровня всё равно получил модификатор.
     */
    fun rollTier(modifierId: String, itemLevel: Int, random: Random = RandomExt.random): ModifierTier? =
        ladders.get()[modifierId]?.roll(itemLevel, random)
}

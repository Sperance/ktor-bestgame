package features.caches

import extensions.weightedRandomExt
import features.logic.modifiers.ModifierTier
import features.logic.modifiers.ModifierTierRepository

class ModifierTierCache(
    repository: ModifierTierRepository
) : MongoCache<ModifierTier, ModifierTierRepository>(repository) {

    /**
     * Все тиры модификатора: от лучшего (тир 1) к худшему.
     */
    fun findByModifier(modifierId: String): List<ModifierTier> =
        getCache().filter { it.modifierId == modifierId }.sortedBy { it.tier }

    /**
     * Взвешенный ролл тира среди доступных на данном item level.
     *
     * Если ни один тир не открыт по item level - берётся самый слабый,
     * чтобы предмет низкого уровня всё равно получил модификатор.
     */
    fun rollTier(modifierId: String, itemLevel: Int): ModifierTier? {
        val tiers = findByModifier(modifierId)
        if (tiers.isEmpty()) return null

        val available = tiers.filter { it.minItemLevel <= itemLevel }
        if (available.isEmpty()) return tiers.last()

        return available.weightedRandomExt { it.weight } ?: available.last()
    }
}

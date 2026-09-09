package features.logic.modifiers

import kotlinx.serialization.Serializable

/**
 * Один tier modifier.
 */
@Serializable
data class ModifierTier(

    /**
     * Номер tier.
     *
     * T1 = самый сильный.
     */
    val tier: Int,

    /**
     * Минимальный item level.
     */
    val minItemLevel: Int = 1,

    /**
     * Вес при генерации.
     *
     * Чем больше weight,
     * тем чаще появляется tier.
     */
    val weight: Int = 100,

    /**
     * Диапазоны значений.
     *
     * Один modifier может иметь несколько значений.
     */
    val values: List<ValueRange> = emptyList()
) {

    init {
        require(tier > 0) {
            "Tier must be greater than zero"
        }

        require(minItemLevel >= 1) {
            "minItemLevel must be >= 1"
        }

        require(weight >= 0) {
            "weight cannot be negative"
        }
    }
}
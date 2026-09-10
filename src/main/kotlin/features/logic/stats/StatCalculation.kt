package features.logic.stats

/**
 * Промежуточное значение расчёта stat.
 */
data class StatCalculation(
    val base: Double = 0.0,
    val flat: Double = 0.0,
    val increased: Double = 0.0,
    val reduced: Double = 0.0,
    val more: Double = 1.0,
    val less: Double = 1.0,
    val set: Double? = null,
    val minimum: Double? = null,
    val maximum: Double? = null
) {

    fun calculate(): Double {

        // INCREASED and REDUCED are additive in one bucket. MORE/LESS multiply.
        val value = set ?: ((base + flat) * (1.0 + increased - reduced) * more * less)
        require(minimum == null || maximum == null || minimum <= maximum) { "Conflicting stat bounds" }
        return value.coerceIn(minimum ?: Double.NEGATIVE_INFINITY, maximum ?: Double.POSITIVE_INFINITY)
    }
}
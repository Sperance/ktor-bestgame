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
    val set: Double? = null
) {

    fun calculate(): Double {

        val baseValue = set ?: (base + flat)

        val increasedValue = baseValue * (1.0 + increased)

        val reducedValue = increasedValue * (1.0 - reduced)

        val moreValue = reducedValue * more

        return moreValue * less
    }
}
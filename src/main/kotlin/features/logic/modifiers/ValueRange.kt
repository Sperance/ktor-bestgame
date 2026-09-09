package features.logic.modifiers

import extensions.to1Digits
import kotlinx.serialization.Serializable
import kotlin.math.pow
import kotlin.math.round
import kotlin.random.Random

@Serializable
data class ValueRange(
    val min: Double,
    val max: Double
) {
    init {
        require(min <= max) {
            "ValueRange min must be <= max"
        }
    }

    fun roll(): Double {
        if (min == max) return min
        return Random.nextDouble(min, max).to1Digits()
    }

    fun roll(decimals: Int): Double {
        val multiplier = 10.0.pow(decimals)
        return round(roll() * multiplier) / multiplier
    }
}
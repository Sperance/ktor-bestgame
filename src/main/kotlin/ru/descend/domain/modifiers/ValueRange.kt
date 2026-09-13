package ru.descend.domain.modifiers

import kotlin.math.pow
import kotlin.math.round
import kotlin.random.Random
import kotlinx.serialization.Serializable
import ru.descend.shared.extensions.to1Digits

@Serializable
@kotlinx.serialization.SerialName("features.logic.modifiers.ValueRange")
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

package features.logic.modifiers

import kotlinx.serialization.Serializable

/**
 * Конкретное значение rolled modifier.
 *
 * Например:
 *
 * +72 Life
 *
 * values = [72.0]
 *
 * Или:
 *
 * Adds 10 to 20 Fire Damage
 *
 * values = [10.0, 20.0]
 */
@Serializable
data class ModifierValue(
    val value: Double
)
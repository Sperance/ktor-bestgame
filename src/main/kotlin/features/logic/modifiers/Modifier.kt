package features.logic.modifiers

import application.enums.EnumModifierDefinitions
import kotlinx.serialization.Serializable

@Serializable
data class Modifier(
    val type: EnumModifierDefinitions,
    val value: Double,
    val tier: Int
)
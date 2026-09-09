package features.logic.modifiers

import kotlinx.serialization.Serializable

/**
 * Modifier после resolution.
 *
 * Definition + rolled values + conditions
 * превращаются в конкретные effects.
 */
@Serializable
data class ResolvedModifier(

    val definitionId: String,

    val tier: Int,

    val source: ModifierSource,

    val effects: List<ModifierEffect>,

    val priority: Int
)
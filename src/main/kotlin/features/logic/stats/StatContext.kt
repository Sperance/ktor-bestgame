package features.logic.stats

import features.logic.modifiers.ModifierContext

data class StatContext(

    val baseStats: Map<StatId, Double> = emptyMap(),

    val modifierContext: ModifierContext = ModifierContext()
)
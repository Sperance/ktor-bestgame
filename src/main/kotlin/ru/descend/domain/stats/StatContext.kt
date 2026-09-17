package ru.descend.domain.stats

import ru.descend.domain.modifiers.ModifierContext

data class StatContext(

    val baseStats: Map<StatId, Double> = emptyMap(),

    val modifierContext: ModifierContext = ModifierContext()
)

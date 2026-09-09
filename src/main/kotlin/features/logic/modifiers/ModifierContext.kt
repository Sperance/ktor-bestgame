package features.logic.modifiers

import features.logic.stats.StatId

/**
 * Контекст вычисления modifier.
 *
 * Это abstraction layer между modifier engine
 * и игровым domain.
 */
data class ModifierContext(

    /**
     * Текущие stats.
     */
    val stats: Map<StatId, Double> = emptyMap(),

    /**
     * Tags персонажа.
     */
    val tags: Set<String> = emptySet(),

    /**
     * Tags цели.
     */
    val targetTags: Set<String> = emptySet(),

    /**
     * Текущие эффекты.
     */
    val effects: Set<String> = emptySet(),

    /**
     * Текущее значение жизни.
     */
    val currentLife: Double? = null,

    /**
     * Максимальная жизнь.
     */
    val maximumLife: Double? = null,

    /**
     * Runtime metadata.
     */
    val metadata: Map<String, Any?> = emptyMap()
)
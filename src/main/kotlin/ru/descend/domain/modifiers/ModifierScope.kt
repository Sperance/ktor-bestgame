package ru.descend.domain.modifiers

import kotlinx.serialization.Serializable

/**
 * Область действия модификатора.
 *
 * Один и тот же modifier может существовать
 * в разных контекстах.
 */
@Serializable
@kotlinx.serialization.SerialName("features.logic.modifiers.ModifierScope")
enum class ModifierScope {

    /**
     * Только предмет.
     */
    ITEM,

    /**
     * Персонаж целиком.
     */
    CHARACTER,

    /**
     * Конкретный навык.
     */
    SKILL,

    /**
     * Атака.
     */
    ATTACK,

    /**
     * Заклинание.
     */
    SPELL,

    /**
     * Попадание.
     */
    HIT,

    /**
     * Цель.
     */
    TARGET,

    /**
     * Область.
     */
    AREA,

    /**
     * Группа.
     */
    PARTY
}

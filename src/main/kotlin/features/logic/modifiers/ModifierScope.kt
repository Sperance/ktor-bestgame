package features.logic.modifiers

import kotlinx.serialization.Serializable

/**
 * Область действия модификатора.
 *
 * Один и тот же modifier может существовать
 * в разных контекстах.
 */
@Serializable
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
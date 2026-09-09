package features.logic.modifiers

import kotlinx.serialization.Serializable

/**
 * Математический способ применения modifier.
 */
@Serializable
enum class ModifierOperation {

    /**
     * +X
     *
     * Пример:
     * +50 Life
     */
    FLAT,

    /**
     * +X%
     *
     * Все такие модификаторы одного stat
     * обычно складываются.
     */
    INCREASED,

    /**
     * -X%
     */
    REDUCED,

    /**
     * X% more.
     *
     * Мультипликативный модификатор.
     */
    MORE,

    /**
     * X% less.
     */
    LESS,

    /**
     * Полностью установить значение.
     */
    SET,

    /**
     * Минимальное значение.
     */
    MIN,

    /**
     * Максимальное значение.
     */
    MAX
}
package features.logic.modifiers

import features.logic.stats.StatId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Выражение для вычисления значения modifier.
 *
 * Позволяет описывать не только:
 *
 * +50 Life
 *
 * но и:
 *
 * +10% Life
 * +5% от Strength
 * 100% increased damage based on X
 * и т.д.
 */
@Serializable
sealed interface ValueExpression {

    /**
     * Константа.
     */
    @Serializable
    @SerialName("constant")
    data class Constant(
        val value: Double
    ) : ValueExpression

    /**
     * Значение другого stat.
     */
    @Serializable
    @SerialName("stat")
    data class Stat(
        val stat: StatId
    ) : ValueExpression

    /**
     * Ссылка на rolled modifier value.
     */
    @Serializable
    @SerialName("modifier_value")
    data class ModifierValue(
        val index: Int = 0
    ) : ValueExpression

    /**
     * Сложение.
     */
    @Serializable
    @SerialName("add")
    data class Add(
        val left: ValueExpression,
        val right: ValueExpression
    ) : ValueExpression

    /**
     * Вычитание.
     */
    @Serializable
    @SerialName("subtract")
    data class Subtract(
        val left: ValueExpression,
        val right: ValueExpression
    ) : ValueExpression

    /**
     * Умножение.
     */
    @Serializable
    @SerialName("multiply")
    data class Multiply(
        val left: ValueExpression,
        val right: ValueExpression
    ) : ValueExpression

    /**
     * Деление.
     */
    @Serializable
    @SerialName("divide")
    data class Divide(
        val left: ValueExpression,
        val right: ValueExpression
    ) : ValueExpression

    /**
     * Процент.
     *
     * 20 -> 0.20
     */
    @Serializable
    @SerialName("percentage")
    data class Percentage(
        val expression: ValueExpression
    ) : ValueExpression

    /**
     * Минимум.
     */
    @Serializable
    @SerialName("min")
    data class Min(
        val left: ValueExpression,
        val right: ValueExpression
    ) : ValueExpression

    /**
     * Максимум.
     */
    @Serializable
    @SerialName("max")
    data class Max(
        val left: ValueExpression,
        val right: ValueExpression
    ) : ValueExpression
}
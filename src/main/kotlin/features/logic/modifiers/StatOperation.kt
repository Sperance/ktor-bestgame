package features.logic.modifiers

import application.enums.EnumModifierOperation
import application.enums.IntEnumStat
import kotlin.math.floor

/**
 * Плоская операция над статом - то, во что калькулятор разворачивает
 * эффекты модификаторов перед расчётом.
 *
 * Развёрнутый вид нужен, чтобы источником операции мог быть не только
 * [Modifier], но и свёрнутый результат локальных модификаторов предмета
 * или база от класса.
 */
data class StatOperation(
    val stat: IntEnumStat,
    val operation: EnumModifierOperation,
    val value: Double,

    /**
     * Стат-источник конверсии, см. [ModifierEffect.perStat].
     */
    val perStat: IntEnumStat? = null,

    /**
     * Сколько единиц источника дают одно [value].
     */
    val perAmount: Double = 1.0,
) {
    /**
     * Итоговое значение операции.
     *
     * У конверсии оно зависит от уже посчитанного источника: как в POE,
     * неполный шаг не засчитывается - 9 Силы при шаге 2 дают 4 применения, не 4.5.
     *
     * @param source значение стата-источника, если операция - конверсия
     */
    fun resolve(source: Double): Double {
        if (perStat == null) return value
        if (perAmount <= 0.0) return 0.0
        return value * floor(source / perAmount)
    }
}

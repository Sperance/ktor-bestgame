package features.logic.modifiers

import application.enums.EnumModifierOperation
import extensions.to1Digits

/**
 * Формула свода модификаторов из POE.
 *
 * `итог = (база + Σ ADD) * (1 + Σ INCREASED / 100) * Π (1 + MORE / 100)`
 *
 * - ADD складываются между собой;
 * - INCREASED складываются между собой и применяются одним множителем;
 * - MORE перемножаются, поэтому каждый следующий такой модификатор ценнее предыдущего;
 * - SET перебивает весь расчёт и применяется последним: "Your Maximum Life is 1"
 *   означает единицу, сколько бы здоровья ни давала экипировка.
 *
 * Вынесена отдельно от [ModifierCalculator], которому для разбора
 * модификаторов нужен справочник: сама арифметика от него не зависит.
 */
object ModifierMath {

    /**
     * Сводит операции одного стата в итоговое значение.
     *
     * @param base базовое значение стата без модификаторов
     * @param operations пары "операция - значение", относящиеся к этому стату
     */
    fun apply(base: Double, operations: Collection<Pair<EnumModifierOperation, Double>>): Double {
        var result = base

        result += operations.filter { it.first == EnumModifierOperation.ADD }.sumOf { it.second }

        val increased = operations.filter { it.first == EnumModifierOperation.INCREASED }.sumOf { it.second }
        result *= (1.0 + increased / 100.0)

        operations.filter { it.first == EnumModifierOperation.MORE }.forEach { (_, value) ->
            result *= (1.0 + value / 100.0)
        }

        // Применяется последним: иначе кейстоун вроде Chaos Inoculation
        // перекрыли бы обычные прибавки с экипировки
        operations.lastOrNull { it.first == EnumModifierOperation.SET }?.let { result = it.second }

        return result.to1Digits()
    }
}

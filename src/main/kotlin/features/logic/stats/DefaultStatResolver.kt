package features.logic.stats

import features.logic.modifiers.ModifierEffect
import features.logic.modifiers.ModifierOperation
import features.logic.modifiers.ResolvedModifier
import features.logic.modifiers.ValueExpressionEvaluator

class DefaultStatResolver(
    private val modifiers: Collection<ResolvedModifier>,
    private val expressionEvaluator: ValueExpressionEvaluator
) : StatResolver {

    override fun resolve(stat: StatId, context: StatContext): Double {

        var calculation = StatCalculation(base = context.baseStats[stat] ?: 0.0)

        val modifierContext = context.modifierContext

        for (modifier in modifiers) {

            for (effect in modifier.effects) {

                if (effect !is ModifierEffect.Stat) {
                    continue
                }

                if (effect.stat != stat) {
                    continue
                }

                val value = expressionEvaluator.evaluate(expression = effect.value, context = modifierContext, modifier = modifier.rolledModifier)

                calculation = apply(calculation, effect.operation, value)
            }
        }

        return calculation.calculate()
    }

    override fun resolveAll(context: StatContext): Map<StatId, Double> {

        val stats = mutableSetOf<StatId>()

        stats += context.baseStats.keys

        modifiers.forEach { modifier ->
            modifier.effects.forEach { effect ->

                if (effect is ModifierEffect.Stat) {
                    stats += effect.stat
                }
            }
        }

        return stats.associateWith { resolve(stat = it, context = context) }
    }

    private fun apply(calculation: StatCalculation, operation: ModifierOperation, value: Double): StatCalculation {

        return when (operation) {

            ModifierOperation.FLAT -> calculation.copy(flat = calculation.flat + value)

            ModifierOperation.INCREASED -> calculation.copy(increased = calculation.increased + value)

            ModifierOperation.REDUCED -> calculation.copy(reduced = calculation.reduced + value)

            ModifierOperation.MORE -> calculation.copy(more = calculation.more * (1.0 + value))

            ModifierOperation.LESS -> calculation.copy(less = calculation.less * (1.0 - value))

            ModifierOperation.SET -> calculation.copy(set = value)

            ModifierOperation.MIN -> calculation.copy(minimum = maxOf(calculation.minimum ?: Double.NEGATIVE_INFINITY, value))

            ModifierOperation.MAX -> calculation.copy(maximum = minOf(calculation.maximum ?: Double.POSITIVE_INFINITY, value))
        }
    }
}
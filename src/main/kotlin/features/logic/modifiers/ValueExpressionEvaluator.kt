package features.logic.modifiers

class ValueExpressionEvaluator {

    fun evaluate(
        expression: ValueExpression,
        context: ModifierContext,
        modifier: Modifier? = null
    ): Double {

        return when (expression) {

            is ValueExpression.Constant -> {
                expression.value
            }

            is ValueExpression.Stat -> {
                context.stats[expression.stat] ?: 0.0
            }

            is ValueExpression.ModifierValue -> {
                modifier?.value(expression.index) ?: 0.0
            }

            is ValueExpression.Add -> {
                evaluate(expression.left, context, modifier) +
                        evaluate(expression.right, context, modifier)
            }

            is ValueExpression.Subtract -> {
                evaluate(expression.left, context, modifier) -
                        evaluate(expression.right, context, modifier)
            }

            is ValueExpression.Multiply -> {
                evaluate(expression.left, context, modifier) *
                        evaluate(expression.right, context, modifier)
            }

            is ValueExpression.Divide -> {
                val divisor = evaluate(
                    expression.right,
                    context,
                    modifier
                )

                if (divisor == 0.0) {
                    0.0
                } else {
                    evaluate(
                        expression.left,
                        context,
                        modifier
                    ) / divisor
                }
            }

            is ValueExpression.Percentage -> {
                evaluate(
                    expression.expression,
                    context,
                    modifier
                ) / 100.0
            }

            is ValueExpression.Min -> {
                minOf(
                    evaluate(expression.left, context, modifier),
                    evaluate(expression.right, context, modifier)
                )
            }

            is ValueExpression.Max -> {
                maxOf(
                    evaluate(expression.left, context, modifier),
                    evaluate(expression.right, context, modifier)
                )
            }
        }
    }
}
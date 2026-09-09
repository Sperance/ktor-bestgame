package features.logic.modifiers

interface ConditionEvaluator {
    fun evaluate(
        condition: ModifierCondition,
        context: ModifierContext
    ): Boolean
}
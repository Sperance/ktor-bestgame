package ru.descend.domain.modifiers

interface ConditionEvaluator {
    fun evaluate(
        condition: ModifierCondition,
        context: ModifierContext
    ): Boolean
}

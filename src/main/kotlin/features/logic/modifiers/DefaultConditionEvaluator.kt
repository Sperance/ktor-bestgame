package features.logic.modifiers

class DefaultConditionEvaluator : ConditionEvaluator {

    override fun evaluate(
        condition: ModifierCondition,
        context: ModifierContext
    ): Boolean {
        return when (condition) {

            ModifierCondition.Always -> true

            is ModifierCondition.StatAtLeast -> {
                context.stats[condition.stat]
                    ?.let { it >= condition.value }
                    ?: false
            }

            is ModifierCondition.StatAtMost -> {
                context.stats[condition.stat]
                    ?.let { it <= condition.value }
                    ?: false
            }

            is ModifierCondition.HasTag -> {
                condition.tag in context.tags
            }

            is ModifierCondition.TargetHasTag -> {
                condition.tag in context.targetTags
            }

            ModifierCondition.FullLife -> {
                val current = context.currentLife
                val maximum = context.maximumLife

                current != null &&
                        maximum != null &&
                        current >= maximum
            }

            ModifierCondition.LowLife -> {
                val current = context.currentLife
                val maximum = context.maximumLife

                current != null &&
                        maximum != null &&
                        maximum > 0.0 &&
                        current / maximum <= 0.35
            }

            is ModifierCondition.HasEffect -> {
                condition.effectId in context.effects
            }

            is ModifierCondition.And -> {
                condition.conditions.all {
                    evaluate(it, context)
                }
            }

            is ModifierCondition.Or -> {
                condition.conditions.any {
                    evaluate(it, context)
                }
            }

            is ModifierCondition.Not -> {
                !evaluate(condition.condition, context)
            }
        }
    }
}
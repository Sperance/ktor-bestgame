package features.logic.modifiers

class DefaultModifierResolver(
    private val definitions: ModifierDefinitionRegistry,
    private val conditionEvaluator: ConditionEvaluator
) : ModifierResolver {

    override fun resolve(
        modifiers: Collection<Modifier>,
        context: ModifierContext
    ): List<ResolvedModifier> {

        return modifiers
            .mapNotNull { modifier ->

                val definition =
                    definitions.find(modifier.definitionId)
                        ?: return@mapNotNull null

                val conditionsPassed =
                    definition.conditions.all {
                        conditionEvaluator.evaluate(
                            it,
                            context
                        )
                    }

                if (!conditionsPassed) {
                    return@mapNotNull null
                }

                ResolvedModifier(
                    definitionId = definition.id,
                    tier = modifier.tier,
                    source = modifier.source,
                    effects = definition.effects,
                    priority = definition.priority
                )
            }
            .sortedBy {
                it.priority
            }
    }
}
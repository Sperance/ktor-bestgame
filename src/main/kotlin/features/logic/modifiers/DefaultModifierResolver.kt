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
                    requireNotNull(definitions.find(modifier.definitionId, modifier.definitionRevision)) {
                        "Missing modifier definition: ${modifier.definitionId}@${modifier.definitionRevision}"
                    }

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
                    priority = definition.priority,
                    rolledModifier = modifier,
                    scope = definition.scope
                )
            }
            .sortedBy {
                it.priority
            }
    }
}
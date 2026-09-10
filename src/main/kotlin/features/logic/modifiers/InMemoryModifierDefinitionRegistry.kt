package features.logic.modifiers

class InMemoryModifierDefinitionRegistry(
    definitions: Collection<ModifierDefinition>
) : ModifierDefinitionRegistry {

    private val byRevision = definitions.sortedBy { it.revision }.associateBy { it.id to it.revision }
    override fun find(id: String, revision: Int) = byRevision[id to revision]

    private val definitionsById =
        definitions.sortedBy { it.revision }.associateBy {
            it.id
        }

    override fun find(
        id: String
    ): ModifierDefinition? {
        return definitionsById[id]
    }

    override fun getAll(): Collection<ModifierDefinition> {
        return definitionsById.values
    }

    override fun findBySource(
        source: ModifierSource
    ): List<ModifierDefinition> {

        return definitionsById
            .values
            .filter {
                it.source == source
            }
    }

    override fun findPrefixes(): List<ModifierDefinition> {
        return definitionsById
            .values
            .filter {
                it.affixType == AffixType.PREFIX
            }
    }

    override fun findSuffixes(): List<ModifierDefinition> {
        return definitionsById
            .values
            .filter {
                it.affixType == AffixType.SUFFIX
            }
    }
}
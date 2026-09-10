package features.logic.modifiers

interface ModifierDefinitionRegistry {

    fun find(id: String): ModifierDefinition?

    fun find(id: String, revision: Int): ModifierDefinition? = find(id)?.takeIf { it.revision == revision }

    fun getAll(): Collection<ModifierDefinition>

    fun findBySource(
        source: ModifierSource
    ): List<ModifierDefinition>

    fun findPrefixes(): List<ModifierDefinition>

    fun findSuffixes(): List<ModifierDefinition>
}
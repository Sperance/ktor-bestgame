package features.logic.modifiers

interface ModifierDefinitionRegistry {

    fun find(id: String): ModifierDefinition?

    fun getAll(): Collection<ModifierDefinition>

    fun findBySource(
        source: ModifierSource
    ): List<ModifierDefinition>

    fun findPrefixes(): List<ModifierDefinition>

    fun findSuffixes(): List<ModifierDefinition>
}
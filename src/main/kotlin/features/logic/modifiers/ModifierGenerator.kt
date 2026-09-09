package features.logic.modifiers

interface ModifierGenerator {

    fun generate(
        definitions: Collection<ModifierDefinition>,
        itemLevel: Int,
        count: Int
    ): List<Modifier>
}
package features.logic.modifiers

interface ModifierResolver {

    fun resolve(
        modifiers: Collection<Modifier>,
        context: ModifierContext
    ): List<ResolvedModifier>
}
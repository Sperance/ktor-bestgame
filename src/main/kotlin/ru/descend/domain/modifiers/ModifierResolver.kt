package ru.descend.domain.modifiers

interface ModifierResolver {

    fun resolve(
        modifiers: Collection<Modifier>,
        context: ModifierContext
    ): List<ResolvedModifier>
}

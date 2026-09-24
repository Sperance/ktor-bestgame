package features.caches

import features.logic.modifiers.ModifierDefinition
import features.logic.modifiers.ModifierDefinitionRepository

class ModifierDefinitionCache(
    repository: ModifierDefinitionRepository
) : MongoCache<ModifierDefinition, ModifierDefinitionRepository>(repository) {

    private val byCode = derived { items -> items.associateBy { it.code } }

    /**
     * Поиск описания модификатора по стабильному коду.
     */
    fun findByCode(code: String): ModifierDefinition? = byCode.get()[code]
}

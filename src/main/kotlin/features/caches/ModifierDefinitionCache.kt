package features.caches

import features.logic.modifiers.ModifierDefinition
import features.logic.modifiers.ModifierDefinitionRepository

class ModifierDefinitionCache(
    repository: ModifierDefinitionRepository
) : MongoCache<ModifierDefinition, ModifierDefinitionRepository>(repository) {

    /**
     * Поиск описания модификатора по стабильному коду.
     */
    fun findByCode(code: String): ModifierDefinition? = getCache().find { it.code == code }

    /**
     * Описания модификаторов по списку id с сохранением порядка переданных id.
     */
    fun findAllById(ids: Collection<String>): List<ModifierDefinition> = ids.mapNotNull { findById(it) }
}

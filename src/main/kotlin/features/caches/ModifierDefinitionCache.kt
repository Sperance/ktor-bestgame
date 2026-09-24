package features.caches

import features.logic.modifiers.ModifierDefinition
import features.logic.modifiers.ModifierDefinitionRepository
import features.logic.pools.Pools
import features.logic.pools.Weighted

class ModifierDefinitionCache(
    repository: ModifierDefinitionRepository
) : MongoCache<ModifierDefinition, ModifierDefinitionRepository>(repository) {

    private val byCode = derived { items -> items.associateBy { it.code } }
    private val pools = derived { items -> Pools.Index(items) }
    private val affixPools = derived { items -> Pools.Index(items.filter { it.isAffix() && !it.crafted }) }

    /**
     * Поиск описания модификатора по стабильному коду.
     */
    fun findByCode(code: String): ModifierDefinition? = byCode.get()[code]

    /** Модификаторы пулов [tags] с их весами, см. [Pools.of]; собирается один раз на ревизию. */
    fun pool(tags: List<String>): List<Weighted<ModifierDefinition>> = pools.get().of(tags)

    /** То же, только аффиксы и не ремесленные - всё, что катают сферы. */
    fun affixPool(tags: List<String>): List<Weighted<ModifierDefinition>> = affixPools.get().of(tags)
}

package features.caches

import features.data.items.Items
import features.data.items.ItemsRepository

class ItemsCache(repository: ItemsRepository) : MongoCache<Items, ItemsRepository>(repository) {
    private val byCode = uniqueIndex { it.code }
    private val byCategory = groupIndex { it.category }

    fun findByCode(code: String): Items? = byCode.get()[code]

    fun findByCategory(category: String): List<Items> = byCategory.get()[category].orEmpty()
}

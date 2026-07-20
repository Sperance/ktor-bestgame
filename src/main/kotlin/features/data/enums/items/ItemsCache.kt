package features.data.enums.items

import extensions.printLog

object ItemsCache {
    private val items = ArrayList<Items>()

    suspend fun initializeCache(repo: ItemsRepository) {
        val data = repo.findAll()
        loadToCache(data)
    }

    fun loadToCache(data: Collection<Items>){
        clearCache()
        items.addAll(data)
        printLog("[${javaClass.simpleName}] initialized cache size: ${items.size}")
    }

    fun addItemToCache(item: Items) = items.add(item)

    fun clearCache() = items.clear()

    fun getCache() = items
}
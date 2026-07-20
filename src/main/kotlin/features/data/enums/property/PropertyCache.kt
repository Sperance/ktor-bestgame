package features.data.enums.property

import extensions.printLog

object PropertyCache {
    private val items = ArrayList<Property>()

    suspend fun initializeCache(repo: PropertyRepository) {
        val data = repo.findAll()
        loadToCache(data)
    }

    fun loadToCache(data: Collection<Property>){
        clearCache()
        items.addAll(data)
        printLog("[${javaClass.simpleName}] initialized cache size: ${items.size}")
    }

    fun addItemToCache(item: Property) = items.add(item)

    fun clearCache() = items.clear()

    fun getCache() = items
}
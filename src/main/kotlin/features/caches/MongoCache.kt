package features.caches

import base.entity.StockEntity
import base.repository.BaseRepository
import extensions.printLog

abstract class MongoCache<T: StockEntity, R: BaseRepository<T>> {

    private val items: ArrayList<T> = arrayListOf()

    suspend fun initializeCache(repo: R) {
        val data = repo.findAll()
        loadToCache(data)
    }

    fun loadToCache(data: Collection<T>) {
        clearCache()
        items.addAll(data)
        printLog("[${javaClass.simpleName}] initialized cache size: ${items.size}")
    }

    fun addItemToCache(item: T) = items.add(item)

    fun clearCache() = items.clear()

    fun getCache() = items

    fun getCacheHash() = items.hashCode()
}
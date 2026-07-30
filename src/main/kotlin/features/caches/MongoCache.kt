package features.caches

import base.entity.StockEntity
import base.repository.BaseRepository
import com.mongodb.kotlin.client.coroutine.ClientSession
import extensions.printLog

abstract class MongoCache<T: StockEntity, R: BaseRepository<T>>(
    val repository: R
) {
    private val items: ArrayList<T> = arrayListOf()

    suspend fun initializeCache() {
        val data = repository.findAll()
        loadToCache(data)
    }

    suspend fun initializeCache(session: ClientSession) {
        val data = repository.findAll(session)
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
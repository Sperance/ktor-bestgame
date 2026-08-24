package features.caches

import base.entity.StockEntity
import base.repository.BaseRepository
import com.mongodb.kotlin.client.coroutine.ClientSession
import extensions.printLog
import kotlinx.coroutines.runBlocking

abstract class MongoCache<T: StockEntity, R: BaseRepository<T>>(val repository: R) {
    private val items: ArrayList<T> = arrayListOf()

    fun initializeCache() = runBlocking {
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

    fun addItem(item: T) = items.add(item)

    fun removeItem(item: T) = items.removeIf { it._id == item._id }

    fun updateItem(item: T) {
        removeItem(item)
        addItem(item)
    }

    fun clearCache() = items.clear()

    fun getCache() = items

    fun getCacheHash() = items.hashCode()

    fun isEmpty() = items.isEmpty()
}
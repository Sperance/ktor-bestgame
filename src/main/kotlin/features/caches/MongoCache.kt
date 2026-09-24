package features.caches

import base.entity.StockEntity
import base.repository.BaseRepository
import com.mongodb.kotlin.client.coroutine.ClientSession
import extensions.printLog
import java.util.concurrent.atomic.AtomicLong

abstract class MongoCache<T: StockEntity, R: BaseRepository<T>>(val repository: R) {
    private val items: ArrayList<T> = arrayListOf()
    private val changes = AtomicLong()

    /**
     * Счётчик правок кеша (с 0.48.0). Растёт при каждой записи, поэтому [features.logic.world.WorldBundle]
     * узнаёт, что справочник изменился, не пересчитывая отпечаток на каждый запрос.
     */
    val revision: Long get() = changes.get()

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
        changes.incrementAndGet()
        printLog("[${javaClass.simpleName}] initialized cache size: ${items.size}")
    }

    fun addItem(item: T) = items.add(item).also { changes.incrementAndGet() }

    fun removeItem(item: T) = items.removeIf { it._id == item._id }.also { changes.incrementAndGet() }

    fun updateItem(item: T) {
        removeItem(item)
        addItem(item)
    }

    fun clearCache() = items.clear().also { changes.incrementAndGet() }

    fun findById(id: String) = items.find { it._id == id }

    fun getCache() = items

    fun getCacheHash() = items.hashCode()

    fun isEmpty() = items.isEmpty()
}
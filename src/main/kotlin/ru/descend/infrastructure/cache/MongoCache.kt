package ru.descend.infrastructure.cache

import ru.descend.shared.model.StockEntity
import ru.descend.infrastructure.mongo.BaseRepository
import com.mongodb.kotlin.client.coroutine.ClientSession
import ru.descend.shared.extensions.printLog
import kotlinx.coroutines.runBlocking

abstract class MongoCache<T: StockEntity, R: BaseRepository<T>>(val repository: R) {
    @Volatile private var items: List<T> = emptyList()

    fun initializeCache() = runBlocking { loadToCache(repository.findAll()) }
    suspend fun initializeCache(session: ClientSession) = loadToCache(repository.findAll(session))

    @Synchronized fun loadToCache(data: Collection<T>) {
        items = data.toList()
        printLog("[${javaClass.simpleName}] initialized cache size: ${items.size}")
    }
    @Synchronized fun addItem(item: T): Boolean { items = items + item; return true }
    @Synchronized fun removeItem(item: T): Boolean {
        val next = items.filterNot { it._id == item._id }
        val changed = next.size != items.size
        items = next
        return changed
    }
    @Synchronized fun updateItem(item: T) { items = items.filterNot { it._id == item._id } + item }
    @Synchronized fun clearCache() { items = emptyList() }
    fun getCache() = ArrayList(items)
    fun getCacheHash() = items.hashCode()
    fun isEmpty() = items.isEmpty()
}

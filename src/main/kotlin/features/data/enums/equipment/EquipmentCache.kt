package features.data.enums.equipment

import extensions.printLog
import features.data.enums.items.Items
import features.data.enums.items.ItemsRepository

object EquipmentCache {
    private val items = ArrayList<Equipment>()

    suspend fun initializeCache(repo: EquipmentRepository) {
        val data = repo.findAll()
        loadToCache(data)
    }

    fun loadToCache(data: Collection<Equipment>){
        clearCache()
        items.addAll(data)
        printLog("[${javaClass.simpleName}] initialized cache size: ${items.size}")
    }

    fun addItemToCache(item: Equipment) = items.add(item)

    fun clearCache() = items.clear()

    fun getCache() = items
}
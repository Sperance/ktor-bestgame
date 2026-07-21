package features.data.enums.equipmentName

import extensions.printLog

object EquipmentNameCache {
    private val items = ArrayList<EquipmentName>()

    suspend fun initializeCache(repo: EquipmentNameRepository) {
        val data = repo.findAll()
        loadToCache(data)
    }

    fun loadToCache(data: Collection<EquipmentName>){
        clearCache()
        items.addAll(data)
        printLog("[${javaClass.simpleName}] initialized cache size: ${items.size}")
    }

    fun addItemToCache(item: EquipmentName) = items.add(item)

    fun clearCache() = items.clear()

    fun getCache() = items
}
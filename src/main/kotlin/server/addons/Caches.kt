package server.addons

import features.caches.BlockListCache
import features.caches.EquipmentCache
import features.caches.EquipmentNameCache
import features.caches.ItemsCache
import features.caches.PropertyCache
import features.data.blockList.BlockListRepository
import features.data.equipment.EquipmentRepository
import features.data.equipmentName.EquipmentNameRepository
import features.data.items.ItemsRepository
import features.data.property.PropertyRepository
import io.ktor.server.application.Application
import org.koin.ktor.ext.inject
import kotlin.getValue

suspend fun Application.configureCaches() {
    val propertyRepository: PropertyRepository by inject()
    PropertyCache.initializeCache(propertyRepository)

    val itemsRepository: ItemsRepository by inject()
    ItemsCache.initializeCache(itemsRepository)

    val equipmentRepository: EquipmentRepository by inject()
    EquipmentCache.initializeCache(equipmentRepository)

    val equipmentNameRepository: EquipmentNameRepository by inject()
    EquipmentNameCache.initializeCache(equipmentNameRepository)

    val blockListRepository: BlockListRepository by inject()
    BlockListCache.initializeCache(blockListRepository)
}
package server.addons

import features.caches.EquipmentCache
import features.caches.EquipmentNameCache
import features.caches.ItemsCache
import features.caches.PropertyCache
import io.ktor.server.application.Application
import org.koin.ktor.ext.inject
import kotlin.getValue

suspend fun Application.configureCaches() {
    val propertyRepository: features.data.property.PropertyRepository by inject()
    PropertyCache.initializeCache(propertyRepository)

    val itemsRepository: features.data.items.ItemsRepository by inject()
    ItemsCache.initializeCache(itemsRepository)

    val equipmentRepository: features.data.equipment.EquipmentRepository by inject()
    EquipmentCache.initializeCache(equipmentRepository)

    val equipmentNameRepository: features.data.equipmentName.EquipmentNameRepository by inject()
    EquipmentNameCache.initializeCache(equipmentNameRepository)
}
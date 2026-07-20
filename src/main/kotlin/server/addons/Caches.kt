package server.addons

import features.data.enums.equipment.EquipmentCache
import features.data.enums.equipment.EquipmentRepository
import features.data.enums.items.ItemsCache
import features.data.enums.items.ItemsRepository
import features.data.enums.property.PropertyCache
import features.data.enums.property.PropertyRepository
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
}
package server.addons

import features.data.items.ItemsCache
import features.data.items.ItemsRepository
import features.data.property.PropertyCache
import features.data.property.PropertyRepository
import io.ktor.server.application.Application
import org.koin.ktor.ext.inject
import kotlin.getValue

suspend fun Application.configureCaches() {
    val propertyRepository: PropertyRepository by inject()
    PropertyCache.initializeCache(propertyRepository)

    val itemsRepository: ItemsRepository by inject()
    ItemsCache.initializeCache(itemsRepository)
}
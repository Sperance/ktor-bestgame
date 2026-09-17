package ru.descend.bootstrap.di

import org.koin.dsl.module
import ru.descend.infrastructure.cache.BlockListCache
import ru.descend.infrastructure.cache.EquipmentCache
import ru.descend.infrastructure.cache.ItemsCache
import ru.descend.infrastructure.cache.RecipeCache

val cacheModule = module {
    single(createdAtStart = true) { BlockListCache(get()).apply { initializeCache() } }
    single(createdAtStart = true) { EquipmentCache(get()).apply { initializeCache() } }
    single(createdAtStart = true) { ItemsCache(get()).apply { initializeCache() } }
    single(createdAtStart = true) { RecipeCache(get()).apply { initializeCache() } }
}


package ru.descend.bootstrap.di

import ru.descend.shared.http.RouteRegistry
import ru.descend.infrastructure.backup.MongoBackupManager
import ru.descend.infrastructure.monitoring.SystemMonitor
import ru.descend.infrastructure.cache.BlockListCache
import ru.descend.infrastructure.cache.EquipmentCache
import ru.descend.infrastructure.cache.ItemsCache
import ru.descend.infrastructure.cache.RecipeCache
import ru.descend.features.blocklist.persistence.BlockListRepository
import ru.descend.features.character.persistence.CharacterRepository
import ru.descend.features.character.http.CharacterRoute
import ru.descend.features.equipment.persistence.EquipmentRepository
import ru.descend.features.equipment.http.EquipmentRoute
import ru.descend.features.items.persistence.ItemsRepository
import ru.descend.features.items.http.ItemsRoute
import ru.descend.features.recipe.persistence.RecipeRepository
import ru.descend.features.recipe.http.RecipeRoute
import ru.descend.features.redemptioncodes.persistence.RedemptionCodesRepository
import ru.descend.features.redemptioncodes.http.RedemptionCodesRoute
import ru.descend.features.user.persistence.UserRepository
import ru.descend.features.user.http.UserRoute
import ru.descend.features.modifiers.persistence.ModifierDefinitionRepository
import org.koin.dsl.module

val cacheModule = module {
    single(createdAtStart = true) { BlockListCache(get()).apply { initializeCache() } }
    single(createdAtStart = true) { EquipmentCache(get()).apply { initializeCache() } }
    single(createdAtStart = true) { ItemsCache(get()).apply { initializeCache() } }
    single(createdAtStart = true) { RecipeCache(get()).apply { initializeCache() } }
}


package application.koin

import base.route.RouteRegistry
import config.MongoBackupManager
import config.SystemMonitor
import features.caches.BlockListCache
import features.caches.EquipmentCache
import features.caches.EquipmentNameCache
import features.caches.ItemsCache
import features.caches.PropertyCache
import features.caches.RecipeCache
import features.data.blockList.BlockListRepository
import features.data.character.CharacterRepository
import features.data.character.CharacterRoute
import features.data.equipment.EquipmentRepository
import features.data.equipment.EquipmentRoute
import features.data.equipmentName.EquipmentNameRepository
import features.data.items.ItemsRepository
import features.data.items.ItemsRoute
import features.data.property.PropertyRepository
import features.data.property.PropertyRoute
import features.data.recipe.RecipeRepository
import features.data.recipe.RecipeRoute
import features.data.user.UserRepository
import features.data.user.UserRoute
import org.koin.dsl.module

val repositoryModule = module {
    single { UserRepository() }
    single { CharacterRepository() }
    single { ItemsRepository() }
    single { PropertyRepository() }
    single { EquipmentRepository() }
    single { EquipmentNameRepository() }
    single { BlockListRepository() }
    single { RecipeRepository() }
}

val cacheModule = module {
    single(createdAtStart = true) { BlockListCache(get()).apply { initializeCache() } }
    single(createdAtStart = true) { EquipmentCache(get()).apply { initializeCache() } }
    single(createdAtStart = true) { EquipmentNameCache(get()).apply { initializeCache() } }
    single(createdAtStart = true) { ItemsCache(get()).apply { initializeCache() } }
    single(createdAtStart = true) { PropertyCache(get()).apply { initializeCache() } }
    single(createdAtStart = true) { RecipeCache(get()).apply { initializeCache() } }
}

val routeModule = module {
    single {
        RouteRegistry(
            listOf(
                UserRoute(get()),
                CharacterRoute(get()),
                ItemsRoute(get()),
                PropertyRoute(get()),
                EquipmentRoute(get()),
                RecipeRoute(get()),
            )
        )
    }
}

val backupModule = module {
    single(createdAtStart = true) {
        MongoBackupManager(
            maxDays = 7,
            maxBackupsCount = 5,
            compress = true
        ).apply {
            start()
        }
    }
}

val systemMonitorModule = module {
    single(createdAtStart = true) {
        SystemMonitor.apply {
            start(intervalHours = 1)
        }
    }
}

val allModules = listOf(
    repositoryModule,
    cacheModule,
    routeModule,
    backupModule,
    systemMonitorModule
)

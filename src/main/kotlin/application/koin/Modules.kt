package application.koin

import base.route.RouteRegistry
import config.MongoBackupManager
import config.SystemMonitor
import features.caches.BlockListCache
import features.caches.EquipmentCache
import features.caches.ItemsCache
import features.caches.ModifierDefinitionCache
import features.caches.ModifierTierCache
import features.caches.RecipeCache
import features.caches.SkillTreeCache
import features.data.blockList.BlockListRepository
import features.data.character.CharacterRepository
import features.data.character.CharacterRoute
import features.data.equipment.EquipmentRepository
import features.data.equipment.EquipmentRoute
import features.data.inventory.CharacterEquipmentRepository
import features.data.inventory.CharacterEquipmentRoute
import features.data.items.ItemsRepository
import features.data.skilltree.CharacterSkillNodeRepository
import features.data.skilltree.CharacterSkillNodeRoute
import features.data.items.ItemsRoute
import features.data.recipe.RecipeRepository
import features.data.recipe.RecipeRoute
import features.data.redemptionCodes.RedemptionCodesRepository
import features.data.redemptionCodes.RedemptionCodesRoute
import features.data.user.UserRepository
import features.data.user.UserRoute
import features.logic.modifiers.ModifierDefinitionRepository
import features.logic.modifiers.ModifierDefinitionRoute
import features.logic.modifiers.ModifierTierRepository
import features.logic.modifiers.ModifierTierRoute
import features.logic.skilltree.SkillTreeNodeRepository
import features.logic.skilltree.SkillTreeNodeRoute
import org.koin.dsl.module

val repositoryModule = module {
    single { UserRepository() }
    single { CharacterRepository() }
    single { CharacterEquipmentRepository() }
    single { ItemsRepository() }
    single { EquipmentRepository() }
    single { BlockListRepository() }
    single { RecipeRepository() }
    single { RedemptionCodesRepository() }
    single { ModifierDefinitionRepository() }
    single { ModifierTierRepository() }
    single { SkillTreeNodeRepository() }
    single { CharacterSkillNodeRepository() }
}

val cacheModule = module {
    single(createdAtStart = true) { BlockListCache(get()).apply { initializeCache() } }
    single(createdAtStart = true) { ModifierDefinitionCache(get()).apply { initializeCache() } }
    single(createdAtStart = true) { ModifierTierCache(get()).apply { initializeCache() } }
    single(createdAtStart = true) { SkillTreeCache(get()).apply { initializeCache() } }
    single(createdAtStart = true) { EquipmentCache(get()).apply { initializeCache() } }
    single(createdAtStart = true) { ItemsCache(get()).apply { initializeCache() } }
    single(createdAtStart = true) { RecipeCache(get()).apply { initializeCache() } }
}

val routeModule = module {
    single {
        RouteRegistry(
            listOf(
                UserRoute(get()),
                CharacterRoute(get()),
                CharacterEquipmentRoute(get()),
                ItemsRoute(get()),
                EquipmentRoute(get()),
                RecipeRoute(get()),
                RedemptionCodesRoute(get()),
                ModifierDefinitionRoute(get()),
                ModifierTierRoute(get()),
                SkillTreeNodeRoute(get()),
                CharacterSkillNodeRoute(get()),
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

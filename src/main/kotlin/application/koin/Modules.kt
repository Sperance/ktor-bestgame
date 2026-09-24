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
import features.caches.CharacterClassCache
import features.caches.ExperienceLevelCache
import features.caches.SkillTreeCache
import features.data.auction.AuctionLotRepository
import features.data.auth.AuthSessionRepository
import features.data.auction.AuctionLotRoute
import features.data.blockList.BlockListRepository
import features.data.character.CharacterRepository
import features.data.character.CharacterRoute
import features.data.equipment.EquipmentRepository
import features.data.equipment.EquipmentRoute
import features.data.inventory.CharacterEquipmentRepository
import features.data.inventory.CharacterEquipmentRoute
import features.data.items.ItemsRepository
import features.data.items.ItemsRoute
import features.data.recipe.RecipeRepository
import features.data.recipe.RecipeRoute
import features.data.redemptionCodes.RedemptionCodesRepository
import features.data.redemptionCodes.RedemptionCodesRoute
import features.data.user.UserRepository
import features.data.user.UserRoute
import features.logic.campaign.CampaignService
import features.logic.modifiers.ModifierDefinitionRepository
import features.logic.modifiers.ModifierDefinitionRoute
import features.logic.modifiers.ModifierTierRepository
import features.logic.modifiers.ModifierTierRoute
import features.logic.progression.CharacterClassRepository
import features.logic.progression.CharacterClassRoute
import features.logic.progression.ExperienceLevelRepository
import features.logic.progression.ExperienceLevelRoute
import features.logic.skilltree.SkillTreeNodeRepository
import features.logic.skilltree.SkillTreeNodeRoute
import org.koin.dsl.module

val repositoryModule = module {
    single { UserRepository() }
    single { AuthSessionRepository() }
    single { CharacterRepository() }
    single { CharacterEquipmentRepository() }
    single { AuctionLotRepository() }
    single { ItemsRepository() }
    single { EquipmentRepository() }
    single { BlockListRepository() }
    single { RecipeRepository() }
    single { RedemptionCodesRepository() }
    single { ModifierDefinitionRepository() }
    single { ModifierTierRepository() }
    single { SkillTreeNodeRepository() }
    single { CharacterClassRepository() }
    single { ExperienceLevelRepository() }
    single { CampaignService() }
    single { features.logic.trade.MerchantService() }
}

val cacheModule = module {
    // Кэши создаются пустыми и наполняются в конце DatabaseSeeder.
    // Грузить их при старте Koin нельзя: они поднимались бы раньше сидера
    // и падали на документах старого формата.
    single { BlockListCache(get()) }
    single { ModifierDefinitionCache(get()) }
    single { ModifierTierCache(get()) }
    single { CharacterClassCache(get()) }
    single { ExperienceLevelCache(get()) }
    single { SkillTreeCache(get()) }
    single { EquipmentCache(get()) }
    single { ItemsCache(get()) }
    single { RecipeCache(get()) }
}

val routeModule = module {
    single {
        RouteRegistry(
            listOf(
                UserRoute(get()),
                CharacterRoute(get(), get(), get()),
                CharacterEquipmentRoute(get()),
                AuctionLotRoute(get()),
                ItemsRoute(get()),
                EquipmentRoute(get()),
                RecipeRoute(get()),
                RedemptionCodesRoute(get()),
                ModifierDefinitionRoute(get()),
                ModifierTierRoute(get()),
                SkillTreeNodeRoute(get()),
                CharacterClassRoute(get()),
                ExperienceLevelRoute(get()),
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

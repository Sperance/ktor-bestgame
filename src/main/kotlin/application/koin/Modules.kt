package application.koin

import base.route.RouteRegistry
import config.ContentStore
import config.MongoBackupManager
import config.SystemMonitor
import features.caches.BlockListCache
import features.data.auction.AuctionLotRepository
import features.data.auction.AuctionLotRoute
import features.data.auth.AuthSessionRepository
import features.data.blockList.BlockListRepository
import features.data.hero.HeroRepository
import features.data.hero.HeroRoute
import features.data.redemptionCodes.RedemptionCodesRepository
import features.data.redemptionCodes.RedemptionCodesRoute
import features.data.user.UserRepository
import features.data.user.UserRoute
import features.logic.atlas.AtlasService
import features.logic.campaign.CampaignService
import features.logic.crafts.CraftsService
import features.logic.inventory.InventoryService
import features.logic.skills.SkillService
import features.logic.trade.MerchantService
import features.logic.tree.TreeService
import org.koin.dsl.module

/** Контент - из файлов ресурсов, один экземпляр на процесс; Mongo его не хранит (1.0.0). */
val contentModule = module {
    single { ContentStore.load() }
}

val repositoryModule = module {
    single { UserRepository() }
    single { AuthSessionRepository() }
    single { HeroRepository() }
    single { AuctionLotRepository() }
    single { BlockListRepository() }
    single { RedemptionCodesRepository() }
    single { InventoryService() }
    single { TreeService() }
    single { AtlasService() }
    single { SkillService() }
    single { CraftsService() }
    single { MerchantService() }
    single { CampaignService() }
}

val cacheModule = module {
    single { BlockListCache(get()) }
}

val routeModule = module {
    single {
        RouteRegistry(
            listOf(
                UserRoute(get(), get()),
                HeroRoute(get(), get(), get(), get(), get(), get(), get(), get(), get()),
                AuctionLotRoute(get()),
                RedemptionCodesRoute(get()),
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
    contentModule,
    repositoryModule,
    cacheModule,
    routeModule,
    backupModule,
    systemMonitorModule
)

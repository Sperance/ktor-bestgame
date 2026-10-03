package ru.descend.exileforge.application.koin

import org.koin.dsl.module
import ru.descend.exileforge.ASANA_BUG_SECTION
import ru.descend.exileforge.ASANA_PROJECT
import ru.descend.exileforge.ASANA_SUGGESTION_SECTION
import ru.descend.exileforge.ASANA_TOKEN
import ru.descend.exileforge.base.route.RouteRegistry
import ru.descend.exileforge.config.ContentStore
import ru.descend.exileforge.config.MongoBackupManager
import ru.descend.exileforge.config.MongoFactory
import ru.descend.exileforge.config.SystemMonitor
import ru.descend.exileforge.features.caches.BlockListCache
import ru.descend.exileforge.features.data.auction.AuctionLotRepository
import ru.descend.exileforge.features.data.auction.AuctionLotRoute
import ru.descend.exileforge.features.data.auth.AuthSessionRepository
import ru.descend.exileforge.features.data.blockList.BlockListRepository
import ru.descend.exileforge.features.data.guild.GuildEventRepository
import ru.descend.exileforge.features.data.guild.GuildRepository
import ru.descend.exileforge.features.data.guild.GuildRoute
import ru.descend.exileforge.features.data.hero.HeroRepository
import ru.descend.exileforge.features.data.hero.HeroRoute
import ru.descend.exileforge.features.data.redemptionCodes.RedemptionCodesRepository
import ru.descend.exileforge.features.data.redemptionCodes.RedemptionCodesRoute
import ru.descend.exileforge.features.data.user.UserRepository
import ru.descend.exileforge.features.data.user.UserRoute
import ru.descend.exileforge.features.logic.atlas.AtlasService
import ru.descend.exileforge.features.logic.campaign.CampaignService
import ru.descend.exileforge.features.logic.crafts.CraftsService
import ru.descend.exileforge.features.logic.inventory.InventoryService
import ru.descend.exileforge.features.logic.pets.PetService
import ru.descend.exileforge.features.logic.quests.QuestService
import ru.descend.exileforge.features.logic.skills.SkillService
import ru.descend.exileforge.features.logic.trade.MerchantService
import ru.descend.exileforge.features.logic.tree.TreeService

/** Контент - из файлов ресурсов, один экземпляр на процесс; Mongo его не хранит (1.0.0). */
val contentModule = module {
    single { ContentStore.load() }
}

val repositoryModule = module {
    single { UserRepository() }
    single { ru.descend.exileforge.features.logic.auth.UserService(get()) }
    single { AuthSessionRepository() }
    single { ru.descend.exileforge.features.data.hero.HeroRunStore(MongoFactory.getDatabase()) }
    single { ru.descend.exileforge.features.data.heroStats.HeroStatsStore(MongoFactory.getDatabase()) }
    single { ru.descend.exileforge.features.data.routeTiming.RouteTimings(MongoFactory.getDatabase()) }
    single { HeroRepository(get(), get(), get()) }
    single { ru.descend.exileforge.features.logic.hero.HeroService(get(), get(), get(), get(), get()) }
    single { AuctionLotRepository() }
    single { ru.descend.exileforge.features.logic.trade.AuctionService(get(), get(), get(), get()) }
    single { GuildRepository() }
    single { GuildEventRepository() }
    single { BlockListRepository() }
    single { RedemptionCodesRepository(get()) }
    single { ru.descend.exileforge.features.logic.redemption.RedemptionService(get(), get(), get(), get()) }
    single<ru.descend.exileforge.features.data.bugReport.FeedbackExport> { ru.descend.exileforge.features.data.bugReport.AsanaExport(ASANA_TOKEN, ASANA_PROJECT, ASANA_BUG_SECTION, ASANA_SUGGESTION_SECTION) }
    single { ru.descend.exileforge.features.data.bugReport.BugReportRepository() }
    single { ru.descend.exileforge.features.data.mail.MailRepository() }
    single { ru.descend.exileforge.features.logic.mail.MailService(get(), get(), get(), get()) }
    single { ru.descend.exileforge.features.logic.feedback.FeedbackService(get(), get(), get(), get(), get()) }
    single { InventoryService(get(), get()) }
    single { TreeService(get(), get()) }
    single { AtlasService(get(), get()) }
    single { SkillService(get(), get()) }
    single { CraftsService(get(), get()) }
    single { MerchantService(get(), get()) }
    single { CampaignService(get(), get(), get(), get()) }
    single { ru.descend.exileforge.features.logic.campaign.TrialService(get(), get(), get()) }
    single { PetService(get(), get()) }
    single { QuestService() }
    single { ru.descend.exileforge.features.logic.hero.ExilePathService(get(), get()) }
    single { ru.descend.exileforge.features.logic.hero.HeroSnapshots(get(), get(), get(), get()) }
    single { ru.descend.exileforge.server.addons.RateKeys(get()) }
}

val cacheModule = module {
    single { BlockListCache(get()) }
}

val routeModule = module {
    single {
        RouteRegistry(
            listOf(
                UserRoute(get(), get(), get()),
                HeroRoute(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()),
                AuctionLotRoute(get(), get()),
                GuildRoute(get()),
                RedemptionCodesRoute(get(), get()),
                ru.descend.exileforge.features.data.bugReport.BugReportRoute(get(), get(), get()),
                ru.descend.exileforge.features.data.admin.AdminRoute(get(), get(), get(), get()),
                ru.descend.exileforge.features.data.mail.MailRoute(get(), get()),
            ),
        )
    }
}

val backupModule = module {
    single(createdAtStart = true) {
        MongoBackupManager(intervalDays = 7, keep = 5).apply { start() }
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
    systemMonitorModule,
)

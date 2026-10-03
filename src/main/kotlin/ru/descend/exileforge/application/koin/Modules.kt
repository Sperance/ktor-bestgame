package ru.descend.exileforge.application.koin

import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import ru.descend.exileforge.ASANA_BUG_SECTION
import ru.descend.exileforge.ASANA_PROJECT
import ru.descend.exileforge.ASANA_SUGGESTION_SECTION
import ru.descend.exileforge.ASANA_TOKEN
import ru.descend.exileforge.base.route.RouteRegistrar
import ru.descend.exileforge.base.route.RouteRegistry
import ru.descend.exileforge.config.ContentStore
import ru.descend.exileforge.config.DatabaseSeeder
import ru.descend.exileforge.config.MongoBackupManager
import ru.descend.exileforge.config.MongoFactory
import ru.descend.exileforge.config.SystemMonitor
import ru.descend.exileforge.features.caches.BlockListCache
import ru.descend.exileforge.features.data.admin.AdminRoute
import ru.descend.exileforge.features.data.auction.AuctionLotRepository
import ru.descend.exileforge.features.data.auction.AuctionLotRoute
import ru.descend.exileforge.features.data.auth.AuthSessionRepository
import ru.descend.exileforge.features.data.blockList.BlockListRepository
import ru.descend.exileforge.features.data.bugReport.AsanaExport
import ru.descend.exileforge.features.data.bugReport.BugReportRepository
import ru.descend.exileforge.features.data.bugReport.BugReportRoute
import ru.descend.exileforge.features.data.bugReport.FeedbackExport
import ru.descend.exileforge.features.data.guild.GuildEventRepository
import ru.descend.exileforge.features.data.guild.GuildRepository
import ru.descend.exileforge.features.data.guild.GuildRoute
import ru.descend.exileforge.features.data.hero.HeroRepository
import ru.descend.exileforge.features.data.hero.HeroRoute
import ru.descend.exileforge.features.data.hero.HeroRunStore
import ru.descend.exileforge.features.data.heroStats.HeroStatsStore
import ru.descend.exileforge.features.data.mail.MailRepository
import ru.descend.exileforge.features.data.mail.MailRoute
import ru.descend.exileforge.features.data.redemptionCodes.RedemptionCodesRepository
import ru.descend.exileforge.features.data.redemptionCodes.RedemptionCodesRoute
import ru.descend.exileforge.features.data.routeTiming.RouteTimings
import ru.descend.exileforge.features.data.user.UserRepository
import ru.descend.exileforge.features.data.user.UserRoute
import ru.descend.exileforge.features.logic.atlas.AtlasService
import ru.descend.exileforge.features.logic.auth.UserService
import ru.descend.exileforge.features.logic.campaign.CampaignService
import ru.descend.exileforge.features.logic.campaign.TrialService
import ru.descend.exileforge.features.logic.crafts.CraftsService
import ru.descend.exileforge.features.logic.feedback.FeedbackService
import ru.descend.exileforge.features.logic.guild.GuildAccess
import ru.descend.exileforge.features.logic.guild.GuildQuestService
import ru.descend.exileforge.features.logic.guild.GuildService
import ru.descend.exileforge.features.logic.guild.GuildStashService
import ru.descend.exileforge.features.logic.guild.GuildTreeService
import ru.descend.exileforge.features.logic.hero.ExilePathService
import ru.descend.exileforge.features.logic.hero.HeroService
import ru.descend.exileforge.features.logic.hero.HeroSnapshots
import ru.descend.exileforge.features.logic.inventory.InventoryService
import ru.descend.exileforge.features.logic.mail.MailService
import ru.descend.exileforge.features.logic.pets.PetService
import ru.descend.exileforge.features.logic.quests.QuestEngine
import ru.descend.exileforge.features.logic.quests.QuestService
import ru.descend.exileforge.features.logic.redemption.RedemptionService
import ru.descend.exileforge.features.logic.skills.SkillService
import ru.descend.exileforge.features.logic.trade.AuctionService
import ru.descend.exileforge.features.logic.trade.MerchantService
import ru.descend.exileforge.features.logic.tree.TreeService
import ru.descend.exileforge.server.addons.RateKeys

/**
 * Граф сервера. Зависимости только через конструкторы (`singleOf`): новый класс объявляется здесь одной
 * строкой, а что он требует - видно по его конструктору. `KoinGraphTest` собирает граф целиком.
 */

/** Контент - из файлов ресурсов, один экземпляр на процесс; Mongo его не хранит (1.0.0). */
val contentModule = module {
    single { ContentStore.load() }
}

/** Коллекции: документ, индексы, выборки. Без игровой логики. */
val repositoryModule = module {
    single { HeroRunStore(MongoFactory.getDatabase()) }
    single { HeroStatsStore(MongoFactory.getDatabase()) }
    single { RouteTimings(MongoFactory.getDatabase()) }
    singleOf(::UserRepository)
    singleOf(::AuthSessionRepository)
    singleOf(::HeroRepository)
    singleOf(::AuctionLotRepository)
    singleOf(::GuildRepository)
    singleOf(::GuildEventRepository)
    singleOf(::BlockListRepository)
    singleOf(::RedemptionCodesRepository)
    singleOf(::BugReportRepository)
    singleOf(::MailRepository)
}

/** Логика: сервисы систем игры и аккаунта. */
val serviceModule = module {
    single<FeedbackExport> { AsanaExport(ASANA_TOKEN, ASANA_PROJECT, ASANA_BUG_SECTION, ASANA_SUGGESTION_SECTION) }
    singleOf(::UserService)
    singleOf(::HeroService)
    singleOf(::AuctionService)
    singleOf(::GuildAccess)
    singleOf(::GuildService)
    singleOf(::GuildTreeService)
    singleOf(::GuildStashService)
    singleOf(::GuildQuestService)
    singleOf(::RedemptionService)
    singleOf(::MailService)
    singleOf(::FeedbackService)
    singleOf(::InventoryService)
    singleOf(::TreeService)
    singleOf(::AtlasService)
    singleOf(::SkillService)
    singleOf(::CraftsService)
    singleOf(::MerchantService)
    singleOf(::CampaignService)
    singleOf(::TrialService)
    singleOf(::PetService)
    singleOf(::QuestEngine)
    singleOf(::QuestService)
    singleOf(::ExilePathService)
    singleOf(::HeroSnapshots)
    singleOf(::RateKeys)
    singleOf(::DatabaseSeeder)
}

val cacheModule = module {
    singleOf(::BlockListCache)
}

/** Маршруты: каждый класс - [RouteRegistrar], реестр собирает их все. */
val routeModule = module {
    singleOf(::UserRoute) { bind<RouteRegistrar>() }
    singleOf(::HeroRoute) { bind<RouteRegistrar>() }
    singleOf(::AuctionLotRoute) { bind<RouteRegistrar>() }
    singleOf(::GuildRoute) { bind<RouteRegistrar>() }
    singleOf(::RedemptionCodesRoute) { bind<RouteRegistrar>() }
    singleOf(::BugReportRoute) { bind<RouteRegistrar>() }
    singleOf(::AdminRoute) { bind<RouteRegistrar>() }
    singleOf(::MailRoute) { bind<RouteRegistrar>() }
    single { RouteRegistry(getAll<RouteRegistrar>()) }
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
    serviceModule,
    cacheModule,
    routeModule,
    backupModule,
    systemMonitorModule,
)

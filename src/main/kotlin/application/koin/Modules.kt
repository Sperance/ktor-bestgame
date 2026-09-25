package application.koin

import base.route.BaseRoute
import base.route.Crud
import base.route.RouteRegistry
import features.data.equipment.equipment_data.Equipment
import features.data.items.Items
import features.logic.modifiers.ModifierDefinition
import features.logic.progression.CharacterClass
import features.logic.progression.ExperienceLevel
import features.logic.skilltree.SkillTreeNode
import config.MongoBackupManager
import config.SystemMonitor
import features.caches.BlockListCache
import features.caches.EquipmentCache
import features.caches.ItemsCache
import features.caches.ModifierDefinitionCache
import features.caches.ModifierTierCache
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
import features.data.inventory.CharacterEquipmentRepository
import features.data.inventory.CharacterEquipmentRoute
import features.data.items.ItemsRepository
import features.data.redemptionCodes.RedemptionCodesRepository
import features.data.redemptionCodes.RedemptionCodesRoute
import features.data.user.UserRepository
import features.data.user.UserRoute
import features.logic.campaign.CampaignService
import features.logic.modifiers.ModifierDefinitionRepository
import features.logic.modifiers.ModifierTierRepository
import features.logic.modifiers.ModifierTierRoute
import features.logic.progression.CharacterClassRepository
import features.logic.progression.ExperienceLevelRepository
import features.logic.skilltree.SkillTreeNodeRepository
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
    single { RedemptionCodesRepository() }
    single { ModifierDefinitionRepository() }
    single { ModifierTierRepository() }
    single { SkillTreeNodeRepository() }
    single { CharacterClassRepository() }
    single { ExperienceLevelRepository() }
    single { CampaignService() }
    single { features.logic.trade.MerchantService() }
    single { features.logic.crafts.CraftsService() }
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
}

val routeModule = module {
    single {
        val catalog = setOf(Crud.READ, Crud.COUNT, Crud.CREATE, Crud.UPDATE, Crud.DELETE)
        val readOnly = setOf(Crud.READ)
        RouteRegistry(
            listOf(
                UserRoute(get(), get()),
                CharacterRoute(get(), get(), get(), get()),
                CharacterEquipmentRoute(get()),
                AuctionLotRoute(get()),
                RedemptionCodesRoute(get()),
                ModifierTierRoute(get()),
                BaseRoute(get<ItemsRepository>(), Items.serializer(), catalog, get<ItemsCache>()),
                // Страницы шаблонов клиент не читает, но требует маршрут при проверке сервера
                BaseRoute(get<EquipmentRepository>(), Equipment.serializer(), catalog + Crud.PAGED, get<EquipmentCache>()),
                BaseRoute(get<ModifierDefinitionRepository>(), ModifierDefinition.serializer(), readOnly, get<ModifierDefinitionCache>()),
                BaseRoute(get<SkillTreeNodeRepository>(), SkillTreeNode.serializer(), readOnly, get<SkillTreeCache>()),
                BaseRoute(get<CharacterClassRepository>(), CharacterClass.serializer(), readOnly, get<CharacterClassCache>()),
                BaseRoute(get<ExperienceLevelRepository>(), ExperienceLevel.serializer(), readOnly, get<ExperienceLevelCache>()),
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

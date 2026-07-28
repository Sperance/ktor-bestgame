package application.koin

import base.route.RouteRegistry
import config.MongoBackupManager
import features.data.blockList.BlockListRepository
import features.data.blockList.BlockListRoute
import features.data.character.CharacterRepository
import features.data.character.CharacterRoute
import features.data.equipment.EquipmentRepository
import features.data.equipment.EquipmentRoute
import features.data.equipmentName.EquipmentNameRepository
import features.data.equipmentName.EquipmentNameRoute
import features.data.items.ItemsRepository
import features.data.items.ItemsRoute
import features.data.property.PropertyRepository
import features.data.property.PropertyRoute
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
                EquipmentNameRoute(get()),
                BlockListRoute(get()),
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

val allModules = listOf(
    repositoryModule,
    routeModule,
    backupModule
)
package application.koin

import base.route.RouteRegistry
import features.data.character.CharacterRepository
import features.data.character.CharacterRoute
import features.data.equipment.EquipmentRepository
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
}

val routeModule = module {
    single {
        RouteRegistry(
            listOf(
                UserRoute(get()),
                CharacterRoute(get()),
                ItemsRoute(get()),
                PropertyRoute(get()),
                EquipmentNameRoute(get()),
            )
        )
    }
}

val allModules = listOf(
    repositoryModule,
    routeModule,
)
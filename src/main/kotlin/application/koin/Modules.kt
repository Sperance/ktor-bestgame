package application.koin

import base.route.RouteRegistry
import features.character.CharacterRepository
import features.character.CharacterRoute
import features.equipment.EquipmentRepository
import features.equipment.EquipmentRoute
import features.items.ItemsRepository
import features.items.ItemsRoute
import features.property.PropertyRepository
import features.property.PropertyRoute
import features.user.UserRepository
import features.user.UserRoute
import org.koin.dsl.module

val repositoryModule = module {
    single { UserRepository() }
    single { CharacterRepository() }
    single { ItemsRepository() }
    single { PropertyRepository() }
    single { EquipmentRepository() }
}

val routeModule = module {
    single {
        RouteRegistry(
            listOf(
                UserRoute(get()),
                CharacterRoute(get()),
                ItemsRoute(get()),
                PropertyRoute(get()),
                EquipmentRoute(get())
            )
        )
    }
}

val allModules = listOf(
    repositoryModule,
    routeModule,
)
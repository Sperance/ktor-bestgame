package application.koin

import base.route.RouteRegistry
import features.data.character.CharacterRepository
import features.data.character.CharacterRoute
import features.data.enums.equipment.EquipmentRepository
import features.data.enums.equipment.EquipmentRoute
import features.data.enums.items.ItemsRepository
import features.data.enums.items.ItemsRoute
import features.data.enums.property.PropertyRepository
import features.data.enums.property.PropertyRoute
import features.data.user.UserRepository
import features.data.user.UserRoute
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
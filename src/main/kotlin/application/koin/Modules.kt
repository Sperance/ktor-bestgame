package application.koin

import features.character.CharacterRepository
import features.character.CharacterRoute
import features.items.ItemsRepository
import features.items.ItemsRoute
import features.property.PropertyRepository
import features.property.PropertyRoute
import features.user.UserRepository
import features.user.UserRoute
import org.koin.dsl.module

// di/RepositoryModule.kt
val repositoryModule = module {
    single { UserRepository() }
    single { CharacterRepository() }
    single { ItemsRepository() }
    single { PropertyRepository() }
}

// di/RouteModule.kt
val routeModule = module {
    single { UserRoute(get()) }
    single { CharacterRoute(get()) }
    single { ItemsRoute(get()) }
    single { PropertyRoute(get()) }
}

// di/AppModules.kt
val allModules = listOf(
    repositoryModule,
    routeModule,
)
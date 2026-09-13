package ru.descend.bootstrap.di

import org.koin.dsl.module
import ru.descend.features.character.http.CharacterRoute
import ru.descend.features.equipment.http.EquipmentRoute
import ru.descend.features.items.http.ItemsRoute
import ru.descend.features.recipe.http.RecipeRoute
import ru.descend.features.redemptioncodes.http.RedemptionCodesRoute
import ru.descend.features.user.http.UserRoute
import ru.descend.shared.http.RouteRegistry

val routeModule = module {
    single {
        RouteRegistry(
            listOf(
                UserRoute(get()),
                CharacterRoute(get()),
                ItemsRoute(get()),
                EquipmentRoute(get()),
                RecipeRoute(get()),
                RedemptionCodesRoute(get()),
            )
        )
    }
}


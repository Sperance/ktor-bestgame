package server.addons

import features.characters.CharacterRoute
import features.equipment.EquipmentRoute
import features.items.ItemsRoute
import features.modifier.configureModifierRoutes
import features.property.PropertyRoute
import features.stats.CharacterStatsRoute
import features.user.UserRoute
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.application.*
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.routing.openapi.OpenApiDocSource
import io.ktor.server.routing.routing
import io.ktor.server.routing.routingRoot

fun Application.configureRouting() {
    routing {
        openAPI(path = "openapi") {
            info = OpenApiInfo("Best Game API", "0.2.15")
            source = OpenApiDocSource.Routing {
                routingRoot.descendants()
            }
        }
        UserRoute().register(this)
        CharacterRoute().register(this)
        EquipmentRoute().register(this)
        CharacterStatsRoute().register(this)
        ItemsRoute().register(this)
        PropertyRoute().register(this)
    }
    configureModifierRoutes()
}

package server.addons

import features.characterMongo.CharacterMongoRoute
import features.equipment.EquipmentRoute
import features.items.ItemsRoute
import features.property.PropertyRoute
import features.stats.CharacterStatsRoute
import features.userMongo.UserMongoRoute
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.application.*
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.routing.openapi.OpenApiDocSource
import io.ktor.server.routing.routing
import io.ktor.server.routing.routingRoot

fun Application.configureRouting() {
    routing {
        EquipmentRoute().register(this)
        CharacterStatsRoute().register(this)
        ItemsRoute().register(this)
        PropertyRoute().register(this)

        /* MONGO */

        UserMongoRoute().register(this)
        CharacterMongoRoute().register(this)

        openAPI(path = "swagger") {
            info = OpenApiInfo("My API", "1.0.1")
            source = OpenApiDocSource.Routing {
                routingRoot.descendants()
            }
        }
    }
}

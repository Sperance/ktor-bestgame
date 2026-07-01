package server.addons

import features.character.CharacterRoute
import features.items.ItemsRoute
import features.property.PropertyRoute
import features.user.UserRoute
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.application.*
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.routing.openapi.OpenApiDocSource
import io.ktor.server.routing.routing
import io.ktor.server.routing.routingRoot

fun Application.configureRouting() {
    routing {
        UserRoute().register(this)
        CharacterRoute().register(this)
        ItemsRoute().register(this)
        PropertyRoute().register(this)

        openAPI(path = "swagger") {
            info = OpenApiInfo("My API", "1.0.1")
            source = OpenApiDocSource.Routing {
                routingRoot.descendants()
            }
        }
    }
}

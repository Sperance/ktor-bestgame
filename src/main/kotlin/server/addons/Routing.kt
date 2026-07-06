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
import org.koin.ktor.ext.inject

fun Application.configureRouting() {
    val userRoute by inject<UserRoute>()
    val characterRoute by inject<CharacterRoute>()
    val itemsRoute by inject<ItemsRoute>()
    val propertyRoute by inject<PropertyRoute>()

    routing {
        userRoute.register(this)
        characterRoute.register(this)
        itemsRoute.register(this)
        propertyRoute.register(this)

        openAPI(path = "swagger") {
            info = OpenApiInfo("My API", "1.0.1")
            source = OpenApiDocSource.Routing {
                routingRoot.descendants()
            }
        }
    }
}

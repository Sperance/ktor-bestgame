package server.addons

import features.post.PostRoute
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
            info = OpenApiInfo("Best Game API", "0.0.1")
            source = OpenApiDocSource.Routing {
                routingRoot.descendants()
            }
        }
        UserRoute().register(this)
        PostRoute().register(this)
    }
}

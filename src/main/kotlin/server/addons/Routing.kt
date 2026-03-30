package server.addons

import io.ktor.openapi.OpenApiInfo
import io.ktor.server.application.*
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.routing.openapi.OpenApiDocSource
import io.ktor.server.routing.routing
import io.ktor.server.routing.routingRoot
import server.routings.configureRoutingUser
import server.tests.configureTestRouting

fun Application.configureRouting() {

    routing {
        openAPI(path = "openapi") {
            info = OpenApiInfo("Best Game API", "0.0.1")
            source = OpenApiDocSource.Routing {
                routingRoot.descendants()
            }
        }
    }

    configureTestRouting()

    configureRoutingUser()
}

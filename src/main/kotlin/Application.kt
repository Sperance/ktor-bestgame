import io.ktor.server.application.Application
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import ru.descend.server.addons.configureHTTP
import ru.descend.server.addons.configureMonitoring
import server.addons.configureRouting
import ru.descend.server.addons.configureSecurity
import ru.descend.server.addons.configureSerialization
import server.addons.configureRouting
import server.tests.configureTestRouting

fun main() {
    embeddedServer(
        Netty,
        configure = {
            connector {
                port = 8080
                host = "0.0.0.0"
            }
        },
        module = {
            configureModules()
        }).start(wait = true)
}

fun Application.configureModules() {
    configureMonitoring()
    configureSerialization()
    configureSecurity()
    configureHTTP()
    configureRouting()

    configureTestRouting()
}
import application.koin.allModules
import extensions.printLog
import io.ktor.server.application.Application
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import server.addons.configureHTTP
import server.addons.configureMonitoring
import server.addons.configureRouting
import server.addons.configureSecurity
import server.addons.configureSerialization
import config.DatabaseSeeder
import org.koin.core.context.startKoin
import server.addons.configureCaches
import server.addons.configureCrypto
import server.addons.configureStatusPages

fun main() {
    printLog("Starting up")

    embeddedServer(
        Netty,
        configure = {
            connector {
                port = 8080
                host = "0.0.0.0"
            }
        },
        module = {
            startKoin {
                modules(allModules)
            }
            configureModules()
        }).start(wait = true)
}

suspend fun Application.configureModules() {
    configureStatusPages()
    configureMonitoring()
    configureSerialization()
    configureSecurity()
    configureHTTP()
    configureRouting()
    configureCrypto()

    DatabaseSeeder.seed()
    configureCaches()
}
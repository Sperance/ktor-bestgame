import application.DatabaseConfig
import extensions.printLog
import io.ktor.server.application.Application
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import ru.descend.server.addons.configureHTTP
import ru.descend.server.addons.configureMonitoring
import server.addons.configureRouting
import server.addons.configureSecurity
import server.addons.configureSerialization
import server.tests.simpleTestData

fun main() {
    printLog("Starting up")
    DatabaseConfig.init()

    val server = embeddedServer(
        Netty,
        configure = {
            connector {
                port = 8080
                host = "0.0.0.0"
            }
        },
        module = {
            configureModules()

            simpleTestData()
        })
    server.start(wait = true)

    Runtime.getRuntime().addShutdownHook(Thread {
        printLog("Stopping the app...")
        DatabaseConfig.close()
        server.stop()
    })
}

fun Application.configureModules() {
    configureMonitoring()
    configureSerialization()
    configureSecurity()
    configureHTTP()
    configureRouting()
}
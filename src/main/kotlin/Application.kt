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
import config.DatabaseSeeder.getKoin
import config.LogManager
import config.MongoBackupManager
import config.SystemMonitor
import io.ktor.server.engine.EmbeddedServer
import org.koin.core.context.startKoin
import server.addons.configureCrypto
import server.addons.configureIpBlocking
import server.addons.configureRateLimit
import server.addons.configureStatusPages

lateinit var server: EmbeddedServer<*, *>

fun main() {
    printLog("\n\n***** Starting up", true)

    server = embeddedServer(Netty,
        configure = {
            connector { port = 8080; host = "0.0.0.0" }
            shutdownGracePeriod = 10_000L },
        module = {
            startKoin { modules(allModules) }
            configureModules()
        }
    )

    Runtime.getRuntime().addShutdownHook(Thread {
        val backupManager: MongoBackupManager = getKoin().get()
        try {
            backupManager.shutdown()
            LogManager.shutdown()
            SystemMonitor.stop()
        } catch (e: Exception) {
            printLog("Error stopping: ${e.message}")
        }
        printLog("\n\n***** Server stopped", true)
    })

    server.start(wait = true)
}

suspend fun Application.configureModules() {
    configureStatusPages()
    configureMonitoring()
    configureSerialization()
    configureSecurity()
    configureHTTP()
    configureRouting()
    configureIpBlocking()
    configureRateLimit()
    configureCrypto()

    DatabaseSeeder.seed()
}
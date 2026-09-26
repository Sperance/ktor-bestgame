import application.koin.allModules
import config.ContentStore
import config.DatabaseSeeder
import config.DatabaseSeeder.getKoin
import config.LogManager
import config.MongoBackupManager
import config.SystemMonitor
import extensions.printLog
import features.logic.icons.IconCache
import features.logic.locale.LocaleCache
import features.logic.portraits.PortraitCache
import io.ktor.server.application.Application
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.koin.core.context.startKoin
import server.addons.configureAccess
import server.addons.configureHTTP
import server.addons.configureIpBlocking
import server.addons.configureMonitoring
import server.addons.configureRateLimit
import server.addons.configureRouting
import server.addons.configureSerialization
import server.addons.configureStatusPages

fun main() {
    printLog("\n\n***** Starting up", true)

    val server = embeddedServer(Netty,
        configure = {
            connector { port = SERVER_PORT; host = "0.0.0.0" }
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
    // Контент читается из ресурсов до всего остального: битый файл роняет старт, а не первый запрос
    val content: ContentStore = getKoin().get()
    LocaleCache.initializeCache()
    IconCache.initializeCache()
    PortraitCache.initializeCache(content.index)

    configureStatusPages()
    configureMonitoring()
    configureSerialization()
    configureHTTP()
    configureRateLimit()
    // Доступ проверяется до маршрутов: каждый запрос под /api и /system проходит здесь.
    configureAccess()
    configureRouting()
    configureIpBlocking()

    DatabaseSeeder.seed()
}

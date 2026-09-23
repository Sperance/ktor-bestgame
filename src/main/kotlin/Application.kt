import application.koin.allModules
import extensions.printLog
import io.ktor.server.application.Application
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import server.addons.configureHTTP
import server.addons.configureMonitoring
import server.addons.configureRouting
import server.addons.configureSerialization
import config.DatabaseSeeder
import config.DatabaseSeeder.getKoin
import config.LogManager
import config.MongoBackupManager
import config.SystemMonitor
import features.logic.icons.IconCache
import features.logic.locale.LocaleCache
import features.logic.portraits.PortraitCache
import io.ktor.server.engine.EmbeddedServer
import org.koin.core.context.startKoin
import server.addons.configureIpBlocking
import server.addons.configureRateLimit
import server.addons.configureAccess
import server.addons.configureStatusPages

lateinit var server: EmbeddedServer<*, *>

fun main() {
    printLog("\n\n***** Starting up", true)

    server = embeddedServer(Netty,
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
    // Словари читаются из ресурсов до всего остального: они не зависят
    // от базы, а поиск на аукционе без них не работает
    LocaleCache.initializeCache()
    // Иконки тоже читаются из ресурсов и тоже ни от чего не зависят
    IconCache.initializeCache()
    // Портреты - из ресурсов; какие искать, говорят классы и кампания
    PortraitCache.initializeCache()

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
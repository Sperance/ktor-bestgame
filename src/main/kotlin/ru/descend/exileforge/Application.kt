package ru.descend.exileforge

import io.ktor.server.application.Application
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.koin.core.context.startKoin
import ru.descend.exileforge.application.koin.allModules
import ru.descend.exileforge.config.ContentStore
import ru.descend.exileforge.config.DatabaseSeeder
import ru.descend.exileforge.config.LogManager
import ru.descend.exileforge.config.MongoBackupManager
import ru.descend.exileforge.config.SystemMonitor
import ru.descend.exileforge.extensions.printLog
import ru.descend.exileforge.features.logic.icons.IconCache
import ru.descend.exileforge.features.logic.locale.LocaleCache
import ru.descend.exileforge.features.logic.portraits.PortraitCache
import ru.descend.exileforge.server.addons.configureAccess
import ru.descend.exileforge.server.addons.configureHTTP
import ru.descend.exileforge.server.addons.configureIpBlocking
import ru.descend.exileforge.server.addons.configureMonitoring
import ru.descend.exileforge.server.addons.configureRateLimit
import ru.descend.exileforge.server.addons.configureRouting
import ru.descend.exileforge.server.addons.configureSerialization
import ru.descend.exileforge.server.addons.configureStatusPages

fun main() {
    printLog("\n\n***** Starting up", true)

    val server = embeddedServer(
        Netty,
        configure = {
            connector {
                port = SERVER_PORT
                host = "0.0.0.0"
            }
            shutdownGracePeriod = 10_000L
        },
        module = {
            // Точка сборки: только здесь и в Modules.kt граф спрашивается у Koin напрямую.
            val koin = startKoin { modules(allModules) }.koin
            configureModules(koin)
        },
    )

    Runtime.getRuntime().addShutdownHook(
        Thread {
            val backupManager: MongoBackupManager = org.koin.core.context.GlobalContext.get().get()
            try {
                backupManager.shutdown()
                LogManager.shutdown()
                SystemMonitor.stop()
            } catch (e: Exception) {
                printLog("Error stopping: ${e.message}")
            }
            printLog("\n\n***** Server stopped", true)
        },
    )

    server.start(wait = true)
}

suspend fun Application.configureModules(koin: org.koin.core.Koin) {
    // Контент читается из ресурсов до всего остального: битый файл роняет старт, а не первый запрос
    val content: ContentStore = koin.get()
    LocaleCache.initializeCache()
    IconCache.initializeCache()
    PortraitCache.initializeCache(content.index)

    configureStatusPages()
    configureMonitoring(koin.get())
    configureSerialization()
    configureHTTP()
    configureRateLimit(koin.get())
    // Доступ проверяется до маршрутов: каждый запрос под /api и /system проходит здесь.
    configureAccess(koin.get(), koin.get(), koin.get(), koin.get())
    configureRouting(koin.get(), koin.get(), koin.get())
    configureIpBlocking(koin.get(), koin.get())

    koin.get<DatabaseSeeder>().seed()
    koin.get<ru.descend.exileforge.features.data.routeTiming.RouteTimings>().start(this)
    ru.descend.exileforge.features.logic.trade.AuctionExpiry(koin.get()).start(this)
}

package ru.descend.bootstrap

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.netty.EngineMain
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import ru.descend.bootstrap.di.allModules
import ru.descend.bootstrap.seed.DatabaseSeeder
import ru.descend.infrastructure.backup.MongoBackupManager
import ru.descend.infrastructure.http.configureCrypto
import ru.descend.infrastructure.http.configureHTTP
import ru.descend.infrastructure.http.configureIpBlocking
import ru.descend.infrastructure.http.configureMonitoring
import ru.descend.infrastructure.http.configureRateLimit
import ru.descend.infrastructure.http.configureRouting
import ru.descend.infrastructure.http.configureSecurity
import ru.descend.infrastructure.http.configureSerialization
import ru.descend.infrastructure.http.configureStatusPages
import ru.descend.infrastructure.http.verifyMongoConnection
import ru.descend.infrastructure.monitoring.LogManager
import ru.descend.infrastructure.monitoring.SystemMonitor

/** Both Gradle and packaged distributions use the same YAML-driven Ktor entry point. */
fun main(args: Array<String>) = EngineMain.main(args)

suspend fun Application.module() {
    // Eager Koin caches read from MongoDB, so connectivity is verified before the graph is built.
    verifyMongoConnection()
    val koin = startKoin { modules(allModules) }.koin
    monitor.subscribe(ApplicationStopped) {
        try { koin.get<MongoBackupManager>().shutdown() }
        finally {
            SystemMonitor.stop()
            LogManager.shutdown()
            stopKoin()
        }
    }
    configureModules()
}

suspend fun Application.configureModules() {
    configureStatusPages()
    configureMonitoring()
    configureSerialization()
    configureSecurity()
    configureHTTP()
    configureIpBlocking()
    configureRateLimit()
    configureCrypto()
    DatabaseSeeder.seed()
    configureRouting()
}

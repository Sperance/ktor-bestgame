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
import config.DatabaseFactory
import config.DatabaseSeeder
import features.characters.CharacterTable
import features.equipment.EquipmentTable
import features.items.ItemsTable
import features.stats.CharacterStatsTable
import features.user.UsersTable
import server.addons.configureStatusPages

fun main() {
    printLog("Starting up")

    DatabaseFactory.init(tables = arrayOf(
        UsersTable,
        CharacterStatsTable,
        EquipmentTable,
        CharacterTable,
        ItemsTable))
    DatabaseSeeder.seed()

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
    configureStatusPages()
    configureMonitoring()
    configureSerialization()
    configureSecurity()
    configureHTTP()
    configureRouting()
}
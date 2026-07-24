import application.koin.allModules
import base.route.ApiMongoResponse
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
import io.ktor.client.request.request
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin
import server.addons.configureCaches
import server.addons.configureCrypto
import server.addons.configureStatusPages
import kotlin.time.Duration.Companion.seconds

lateinit var server: EmbeddedServer<*, *>

fun main() {
    printLog("\n\n***** Starting up")

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
        printLog("***** Server stopped")
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
    configureSystem()
    configureCrypto()

    DatabaseSeeder.seed()
    configureCaches()
}

@OptIn(DelicateCoroutinesApi::class)
private fun Application.configureSystem() {
    routing {
        route("/system") {
            get("/shutdown") {

                val key = call.queryParameters["key"]
                if (key == null || key != "32543254") {
                    call.respond(ApiMongoResponse.ok("Access denied"))
                    return@get
                }

                call.respond(ApiMongoResponse.ok("Success"))

                GlobalScope.launch {
                    delay(2.seconds)
                    server.stop()
                }
            }
        }
    }
}
package server.addons

import CORS_HOSTS
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlin.time.Duration.Companion.seconds

fun Application.configureHTTP() {
    install(DefaultHeaders) {
        header("X-Engine", "Ktor") // will send this header with each response
    }
    // Сокет лобби кооператива (1.23.0): пинг держит мобильную связь, мёртвый сокет закрывается сам.
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 30.seconds
        maxFrameSize = 1L shl 20
    }
    // Приложению на Android CORS не нужен вовсе - это правило браузеров. Поэтому по умолчанию
    // из браузера не пускается никто, а веб-клиенту адрес разрешают явно, через CORS_HOSTS.
    if (CORS_HOSTS.isNotEmpty()) install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        CORS_HOSTS.forEach { host ->
            val scheme = host.substringBefore("://", "https")
            allowHost(host.substringAfter("://"), schemes = listOf(scheme))
        }
    }
}

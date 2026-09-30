package server.addons

import CORS_HOSTS
import TRUSTED_PROXIES
import io.ktor.server.plugins.mutableOriginConnectionPoint
import features.logic.hero.HeroSnapshots
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*

/** Доверяет ли [TRUSTED_PROXIES] сокету с адреса [address]. */
private fun trusted(address: String): Boolean = TRUSTED_PROXIES.any { it == "*" || it == address }

/**
 * Адрес игрока за прокси (1.53.0): когда сокет открыт доверенным прокси, адресом запроса становится первый
 * `X-Forwarded-For` (или `X-Real-IP`) - по нему считают лимиты и блок-лист. С чужого сокета заголовки не читаются:
 * иначе любой клиент подставил бы адрес и обошёл лимит входа.
 */
private fun Application.configureTrustedProxy() {
    if (TRUSTED_PROXIES.isEmpty()) return
    intercept(ApplicationCallPipeline.Setup) {
        if (!trusted(call.request.local.remoteAddress)) return@intercept
        val forwarded = call.request.headers["X-Forwarded-For"]?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() }
            ?: call.request.headers["X-Real-IP"]?.trim()?.takeIf { it.isNotEmpty() } ?: return@intercept
        call.mutableOriginConnectionPoint.remoteHost = forwarded
        call.mutableOriginConnectionPoint.remoteAddress = forwarded
    }
}

fun Application.configureHTTP() {
    configureTrustedProxy()
    install(DefaultHeaders) {
        header("X-Engine", "Ktor") // will send this header with each response
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
        allowHeader(Idempotency.HEADER)
        allowHeader(HeroSnapshots.HEADER)
        exposeHeader(Idempotency.REPLAY_HEADER)
        CORS_HOSTS.forEach { host ->
            val scheme = host.substringBefore("://", "https")
            allowHost(host.substringAfter("://"), schemes = listOf(scheme))
        }
    }
}

package server.addons

import features.logic.auth.Tokens
import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import kotlin.time.Duration.Companion.minutes

/** Лимит на вход: пароли и идентификаторы устройств подбираются именно здесь. */
val LOGIN_LIMIT = RateLimitName("login")

/**
 * Лимиты запросов.
 *
 * До 0.21.0 лимит был один на весь сервер - тысяча запросов в минуту на всех, так что один
 * активный клиент выедал квоту остальным. Теперь у каждого клиента своя корзина: по токену,
 * а без него - по адресу. Вход ограничен отдельно и строго, по адресу: у того, кто подбирает
 * пароль, токена ещё нет.
 */
fun Application.configureRateLimit() {
    install(RateLimit) {
        global {
            rateLimiter(limit = 600, refillPeriod = 1.minutes)
            requestKey { call -> Tokens.fromHeader(call.request.headers[HttpHeaders.Authorization]) ?: call.request.origin.remoteAddress }
        }
        register(LOGIN_LIMIT) {
            rateLimiter(limit = 10, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteAddress }
        }
    }
}

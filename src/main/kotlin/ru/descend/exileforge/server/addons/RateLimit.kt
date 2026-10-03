package ru.descend.exileforge.server.addons
import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import org.koin.mp.KoinPlatform.getKoin
import ru.descend.exileforge.base.cache.BoundedCache
import ru.descend.exileforge.features.data.auth.AuthSessionRepository
import ru.descend.exileforge.features.logic.auth.Tokens
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Лимит на вход: пароли и секреты устройств подбираются именно здесь. */
val LOGIN_LIMIT = RateLimitName("login")

/** Промокоды (1.46.0): перебор кодов. */
val REDEEM_LIMIT = RateLimitName("redeem")

/** Смена пароля (1.46.0): перебор старого пароля под чужой сессией. */
val PASSWORD_LIMIT = RateLimitName("password")

/** Отчёты об ошибках (1.46.0): их пишут и без входа. */
val BUG_LIMIT = RateLimitName("bug")

/**
 * Лимиты запросов.
 *
 * У каждого клиента своя корзина (0.21.0). С 1.46.0 корзина - аккаунт живой сессии, а не строка токена:
 * выдуманные токены прежде давали каждый свою корзину и обходили лимит. Без живой сессии - адрес.
 * Вход ограничен отдельно и строго, по адресу: у того, кто подбирает пароль, токена ещё нет.
 */
fun Application.configureRateLimit() {
    install(RateLimit) {
        global {
            rateLimiter(limit = 600, refillPeriod = 1.minutes)
            requestKey { call -> RateKeys.of(call) }
        }
        register(LOGIN_LIMIT) {
            rateLimiter(limit = 10, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteAddress }
        }
        register(REDEEM_LIMIT) {
            rateLimiter(limit = 5, refillPeriod = 1.minutes)
            requestKey { call -> RateKeys.of(call) }
        }
        register(PASSWORD_LIMIT) {
            rateLimiter(limit = 5, refillPeriod = 1.minutes)
            requestKey { call -> RateKeys.of(call) }
        }
        register(BUG_LIMIT) {
            rateLimiter(limit = 100, refillPeriod = 1.hours)
            requestKey { call -> RateKeys.of(call) }
        }
    }
}

/**
 * Ключ корзины: `user:<id>` живой сессии или `ip:<адрес>`. Сессия по токену помнится минуту, чтобы лимит не стоил
 * каждому запросу чтения из базы; неизвестный токен помнится так же - перебор токенов не бьёт в базу.
 */
object RateKeys {
    private const val TTL = 60_000L
    private const val MAX = 50_000
    private val known = BoundedCache<String, String?>(MAX, TTL)
    private val sessions: AuthSessionRepository by lazy { getKoin().get() }

    suspend fun of(call: ApplicationCall): String {
        val token = Tokens.fromHeader(call.request.headers[HttpHeaders.Authorization])
        val user = token?.let { userOf(it) }
        return user?.let { "user:$it" } ?: "ip:${call.request.origin.remoteAddress}"
    }

    private suspend fun userOf(token: String): String? {
        val key = Tokens.hash(token)
        known.lookup(key)?.let { return it.value }
        val user = runCatching { sessions.resolve(token)?.userId }.getOrNull()
        known.put(key, user)
        return user
    }
}

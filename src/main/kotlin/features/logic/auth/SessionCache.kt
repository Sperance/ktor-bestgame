package features.logic.auth

import base.cache.BoundedCache
import features.data.auth.AuthSession
import features.data.user.User

/**
 * Кеш входа (1.53.0): сессия и аккаунт по отпечатку токена на [TTL_MS], чтобы каждая команда героя не читала
 * две коллекции ради проверки доступа. Сервер один, поэтому кеш в памяти безопасен: выход, смена пароля и
 * блокировка гасят записи сразу ([evictToken], [evictUser]), остальное доезжает за минуту.
 */
object SessionCache {
    private class Entry(val session: AuthSession, val user: User, val at: Long)

    const val TTL_MS = 60_000L

    /** Больше записей кеш не держит: переполнение вытесняет самую давно прочитанную. */
    private const val MAX = 20_000

    private val entries = BoundedCache<String, Entry>(MAX)

    fun get(tokenHash: String, now: Long = System.currentTimeMillis()): Pair<AuthSession, User>? = entries.get(tokenHash)?.takeIf { now - it.at < TTL_MS }?.let { it.session to it.user }

    fun put(tokenHash: String, session: AuthSession, user: User, now: Long = System.currentTimeMillis()) {
        entries.put(tokenHash, Entry(session, user, now))
    }

    fun evictToken(tokenHash: String) {
        entries.remove(tokenHash)
    }

    fun evictUser(userId: String) {
        entries.removeIf { it.user._id == userId }
    }

    fun clear() = entries.clear()
}

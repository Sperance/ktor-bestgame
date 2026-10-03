package ru.descend.exileforge.features.data.auth
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import ru.descend.exileforge.base.repository.BaseRepository
import ru.descend.exileforge.base.repository.IndexSpec
import ru.descend.exileforge.config.MongoFactory.transactionExecute
import ru.descend.exileforge.extensions.now
import ru.descend.exileforge.features.logic.auth.SessionCache
import ru.descend.exileforge.features.logic.auth.Tokens
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Сессии входа: выдать, узнать по токену, отозвать.
 */
class AuthSessionRepository : BaseRepository<AuthSession>(entityClass = AuthSession::class) {

    companion object {
        /** Сколько живёт сессия без использования. */
        val LIFETIME: Duration = 30.days

        /**
         * Как часто срок сдвигается в базе. Писать в базу на каждый запрос незачем:
         * час точности на тридцати днях ничего не меняет.
         */
        private val TOUCH_EVERY: Duration = 1.hours

        /** Живых сессий на аккаунт (1.46.0): новая вытесняет самую старую по последнему входу. */
        const val MAX_PER_USER = 5
    }

    override val indexes = listOf(IndexSpec.unique("idx_unique_token", "tokenHash"), IndexSpec.on("userId"))

    /** Новая сессия для аккаунта; возвращает сам токен - второй раз его взять будет неоткуда. */
    suspend fun issue(userId: String): String {
        val token = Tokens.issue()
        val now = LocalDateTime.now()
        // Заодно подчищаются истёкшие сессии этого аккаунта, чтобы коллекция не росла вечно.
        // Сроки сравниваются здесь, а не запросом к базе: даты хранятся в том виде, в каком их
        // пишет сериализатор, и сравнивать их на стороне Mongo значит полагаться на формат.
        val (expired, alive) = collection.find(Filters.eq("userId", userId)).toList().partition { it.expiresAt < now }
        val crowded = alive.sortedBy { it.lastUsedAt }.dropLast(MAX_PER_USER - 1)
        val gone = (expired + crowded).map { it._id }
        transactionExecute("auth issue") { session ->
            if (gone.isNotEmpty()) collection.deleteMany(session, Filters.`in`("_id", gone))
            insert(AuthSession(userId = userId, tokenHash = Tokens.hash(token), expiresAt = now.plus(LIFETIME)), session)
        }
        return token
    }

    /** Живая сессия по токену, или null; истёкшая удаляется, живая продлевается. */
    suspend fun resolve(token: String): AuthSession? {
        val found = collection.find(Filters.eq("tokenHash", Tokens.hash(token))).limit(1).firstOrNull() ?: return null
        val now = LocalDateTime.now()
        if (found.expiresAt < now) {
            collection.deleteOne(Filters.eq("_id", found._id))
            return null
        }
        if (found.lastUsedAt.plus(TOUCH_EVERY) < now) {
            collection.updateOne(
                Filters.eq("_id", found._id),
                Updates.combine(Updates.set("lastUsedAt", now), Updates.set("expiresAt", now.plus(LIFETIME))),
            )
        }
        return found
    }

    suspend fun revoke(token: String) {
        val hash = Tokens.hash(token)
        SessionCache.evictToken(hash)
        collection.deleteOne(Filters.eq("tokenHash", hash))
    }

    /** Все сессии аккаунта, кроме, возможно, текущей - после смены пароля или блокировки. */
    suspend fun revokeAll(userId: String, except: String? = null) {
        SessionCache.evictUser(userId)
        val filter = if (except == null) {
            Filters.eq("userId", userId)
        } else {
            Filters.and(Filters.eq("userId", userId), Filters.ne("tokenHash", Tokens.hash(except)))
        }
        collection.deleteMany(filter)
    }

    private fun LocalDateTime.plus(duration: Duration): LocalDateTime = toInstant(TimeZone.UTC).plus(duration).toLocalDateTime(TimeZone.UTC)
}

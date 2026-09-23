package features.data.auth

import base.repository.BaseRepository
import base.repository.UniqueIndexConfig
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import config.MongoFactory.transactionExecute
import extensions.now
import features.logic.auth.Tokens
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
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
    }

    init {
        initialize(
            uniqueIndexes = listOf(UniqueIndexConfig(indexName = "idx_unique_token", fields = listOf("tokenHash"))),
            indexedFields = listOf("userId"),
        )
    }

    /** Новая сессия для аккаунта; возвращает сам токен - второй раз его взять будет неоткуда. */
    suspend fun issue(userId: String): String {
        val token = Tokens.issue()
        val now = LocalDateTime.now()
        // Заодно подчищаются истёкшие сессии этого аккаунта, чтобы коллекция не росла вечно.
        // Сроки сравниваются здесь, а не запросом к базе: даты хранятся в том виде, в каком их
        // пишет сериализатор, и сравнивать их на стороне Mongo значит полагаться на формат.
        val expired = collection.find(Filters.eq("userId", userId)).toList().filter { it.expiresAt < now }.map { it._id }
        transactionExecute("auth issue") { session ->
            if (expired.isNotEmpty()) collection.deleteMany(session, Filters.`in`("_id", expired))
            insert(AuthSession(userId = userId, tokenHash = Tokens.hash(token), expiresAt = now.plus(LIFETIME)), session)
        }
        return token
    }

    /** Живая сессия по токену, или null; истёкшая удаляется, живая продлевается. */
    suspend fun resolve(token: String): AuthSession? {
        val found = collection.find(Filters.eq("tokenHash", Tokens.hash(token))).toList().firstOrNull() ?: return null
        val now = LocalDateTime.now()
        if (found.expiresAt < now) {
            collection.deleteOne(Filters.eq("_id", found._id))
            return null
        }
        if (found.lastUsedAt.plus(TOUCH_EVERY) < now) {
            collection.updateOne(Filters.eq("_id", found._id),
                Updates.combine(Updates.set("lastUsedAt", now), Updates.set("expiresAt", now.plus(LIFETIME))))
        }
        return found
    }

    suspend fun revoke(token: String) {
        collection.deleteOne(Filters.eq("tokenHash", Tokens.hash(token)))
    }

    /** Все сессии аккаунта, кроме, возможно, текущей - после смены пароля или блокировки. */
    suspend fun revokeAll(userId: String, except: String? = null) {
        val filter = if (except == null) Filters.eq("userId", userId)
            else Filters.and(Filters.eq("userId", userId), Filters.ne("tokenHash", Tokens.hash(except)))
        collection.deleteMany(filter)
    }

    private fun LocalDateTime.plus(duration: Duration): LocalDateTime =
        toInstant(TimeZone.UTC).plus(duration).toLocalDateTime(TimeZone.UTC)
}

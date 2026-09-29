package features.data.idempotency

import com.mongodb.MongoWriteException
import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import config.MongoFactory
import kotlinx.coroutines.flow.firstOrNull
import org.bson.Document
import org.bson.types.Binary
import server.addons.Claim
import server.addons.Idempotency
import server.addons.ReplyStore
import server.addons.StoredReply
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Ответы команд по `Idempotency-Key` в коллекции `IdempotentReply` (1.28.0). `_id` - пара
 * `аккаунт:ключ`, так что уникальность пары держит сама база: из двух одновременных вставок
 * проходит одна. Документ сперва «в работе», после ответа - «готов» со статусом и телом; TTL-индекс
 * по `createdAt` убирает его через сутки. Брошенный «в работе» (сервер упал посреди команды)
 * через [STALE_MS] считается свободным.
 */
object IdempotentReplyStore : ReplyStore {
    private const val COLLECTION = "IdempotentReply"
    private const val DUPLICATE_KEY = 11000
    private const val STALE_MS = 2 * 60_000L
    private const val PENDING = "pending"
    private const val DONE = "done"

    private val collection: MongoCollection<Document> by lazy { MongoFactory.getDatabase().getCollection(COLLECTION, Document::class.java) }

    private fun id(account: String, key: String) = "$account:$key"

    /** Индекс срока жизни - при старте, до первой транзакции, как индексы репозиториев. */
    suspend fun ensureIndexes() {
        runCatching {
            collection.createIndex(Indexes.ascending("createdAt"),
                IndexOptions().name("idx_ttl_created").expireAfter(Idempotency.TTL_HOURS, TimeUnit.HOURS))
        }
    }

    override suspend fun claim(account: String, key: String): Claim {
        val id = id(account, key)
        repeat(2) {
            try {
                collection.insertOne(Document("_id", id).append("account", account).append("key", key)
                    .append("state", PENDING).append("createdAt", Date()))
                return Claim.Taken
            } catch (e: MongoWriteException) {
                if (e.code != DUPLICATE_KEY) throw e
            }
            val found = collection.find(Filters.eq("_id", id)).firstOrNull() ?: return@repeat
            if (found.getString("state") == DONE) return Claim.Done(
                StoredReply(found.getInteger("status"), found.getString("contentType"), found.get("body", Binary::class.java).data))
            val since = found.getDate("createdAt")?.time ?: 0L
            if (System.currentTimeMillis() - since < STALE_MS) return Claim.Busy
            collection.deleteOne(Filters.and(Filters.eq("_id", id), Filters.eq("state", PENDING)))
        }
        return Claim.Busy
    }

    override suspend fun complete(account: String, key: String, reply: StoredReply) {
        collection.updateOne(Filters.eq("_id", id(account, key)), Updates.combine(
            Updates.set("state", DONE),
            Updates.set("status", reply.status),
            Updates.set("contentType", reply.contentType),
            Updates.set("body", Binary(reply.body)),
        ))
    }

    override suspend fun release(account: String, key: String) {
        collection.deleteOne(Filters.and(Filters.eq("_id", id(account, key)), Filters.eq("state", PENDING)))
    }
}

package config

import MONGO_DB
import MONGO_URI
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import extensions.printLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.bson.Document
import org.bson.codecs.configuration.CodecRegistries
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

object MongoFactory {
    private var mongoClient = createMongoClient()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isReconnecting = false

    fun getDatabase(): MongoDatabase {
        return try {
            mongoClient.getDatabase(MONGO_DB)
        } catch (e: Exception) {
            printLog("[MongoFactory] Failed to get database, attempting reconnect", true)
            reconnect()
            mongoClient.getDatabase(MONGO_DB)
        }
    }

    private fun createMongoClient(connectionString: String = MONGO_URI): MongoClient {
        val codecRegistry = CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry()
        )

        val settings = MongoClientSettings.builder()
            .applyConnectionString(ConnectionString(connectionString))
            .codecRegistry(codecRegistry)
            .timeout(10, TimeUnit.MINUTES)
            .build()

        return MongoClient.create(settings)
    }

    private fun reconnect() {
        if (isReconnecting) return
        isReconnecting = true

        scope.launch {
            try {
                printLog("[MongoFactory] Attempting to reconnect to MongoDB...", true)

                try {
                    mongoClient.close()
                } catch (_: Exception) {}

                val newClient = createMongoClient()
                newClient.getDatabase("admin").runCommand(Document("ping", 1))

                printLog("[MongoFactory] ✅ Reconnected to MongoDB successfully", true)
                mongoClient = newClient

            } catch (e: Exception) {
                e.printStackTrace()
                printLog("[MongoFactory] Failed to reconnect to MongoDB", true)

                if (isActive) delay(5000.milliseconds)
                if (isActive) reconnect()
            } finally {
                isReconnecting = false
            }
        }
    }

    /** Enable body retries only when reads and mutable entities are rebuilt inside [body]. */
    suspend fun <T> transactionExecute(
        transactionName: String = "",
        retryTransientErrors: Boolean = false,
        body: suspend (ClientSession) -> T
    ): T = mongoClient.startSession().use { session ->
        runTransaction(
            start = {
                printLog("[TR::start::${session.hashCode()}] $transactionName", true)
                session.startTransaction()
            },
            active = { session.hasActiveTransaction() },
            commit = {
                session.commitTransaction()
                printLog("[TR::committed::${session.hashCode()}] $transactionName", true)
            },
            abort = { session.abortTransaction() },
            maxAttempts = if (retryTransientErrors) 5 else 1,
            body = { body(session) }
        )
    }
}

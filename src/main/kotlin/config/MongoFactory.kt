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
import kotlinx.coroutines.launch
import org.bson.Document
import org.bson.codecs.configuration.CodecRegistries
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

    private fun createMongoClient(connectionString: String = "$MONGO_URI/$MONGO_DB"): MongoClient {
        val codecRegistry = CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry()
        )

        val settings = MongoClientSettings.builder()
            .applyConnectionString(ConnectionString(connectionString))
            .codecRegistry(codecRegistry)
            .build()

        return MongoClient.create(settings)
    }

    private fun reconnect() {
        if (isReconnecting) return
        isReconnecting = true

        scope.launch {
            try {
                printLog("[MongoFactory] Attempting to reconnect to MongoDB...", true)

                // Закрываем старый клиент
                try {
                    mongoClient.close()
                } catch (_: Exception) {
                    // игнорируем
                }

                // Создаём новый клиент
                val newClient = createMongoClient()

                // Проверяем подключение
                newClient.getDatabase("admin").runCommand(Document("ping", 1))

                printLog("[MongoFactory] ✅ Reconnected to MongoDB successfully", true)

                mongoClient = newClient

            } catch (e: Exception) {
                e.printStackTrace()
                printLog("[MongoFactory] Failed to reconnect to MongoDB", true)

                // Пробуем снова через 5 секунд
                delay(5000.milliseconds)
                reconnect()
            } finally {
                isReconnecting = false
            }
        }
    }

    suspend fun <T> transactionExecute(transactionName: String = "", body: suspend (ClientSession) -> T): T {
        mongoClient.startSession().use { session ->
            printLog("[TR::start::${session.hashCode()}] $transactionName ", true)
            session.startTransaction()
            try {
                val result = body(session)
                if (session.hasActiveTransaction()) {
                    printLog("[TR::commit${session.hashCode()}] $transactionName ", true)
                    session.commitTransaction()
                }
                return result
            } catch (e: Exception) {
                if (session.hasActiveTransaction()) {
                    printLog("[TR::abort${session.hashCode()}] $transactionName ", true)
                    session.abortTransaction()
                }
                throw e
            }
        }
    }
}
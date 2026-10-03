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
import kotlinx.coroutines.withContext
import org.bson.Document
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.configuration.CodecRegistry
import server.addons.AppJson
import server.addons.CommandKey
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.milliseconds

object MongoFactory {
    /**
     * Клиент один на процесс: переподключение к серверу, выбор узла и повтор чтения/записи драйвер делает сам.
     * Замена клиента - только если он не отдаёт базу вовсе; старый закрывается уже после публикации нового,
     * так что ни один поток не получит закрытый клиент посреди запроса.
     */
    @Volatile private var mongoClient = createMongoClient()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val reconnecting = AtomicBoolean(false)

    fun getDatabase(): MongoDatabase = try {
        mongoClient.getDatabase(MONGO_DB)
    } catch (e: Exception) {
        printLog("[MongoFactory] Failed to get database, attempting reconnect", true)
        reconnect()
        mongoClient.getDatabase(MONGO_DB)
    }

    private fun createMongoClient(connectionString: String = "$MONGO_URI/$MONGO_DB"): MongoClient {
        val codecRegistry = CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
        )

        val settings = MongoClientSettings.builder()
            .applyConnectionString(ConnectionString(connectionString))
            .codecRegistry(codecRegistry)
            .retryWrites(true)
            .retryReads(true)
            .build()

        return MongoClient.create(settings)
    }

    private fun reconnect() {
        if (!reconnecting.compareAndSet(false, true)) return
        scope.launch {
            try {
                while (isActive) {
                    val fresh = createMongoClient()
                    val alive = runCatching { fresh.getDatabase("admin").runCommand(Document("ping", 1)) }
                        .onFailure {
                            printLog("[MongoFactory] Failed to reconnect to MongoDB: ${it.message}", true)
                            runCatching { fresh.close() }
                        }
                        .isSuccess
                    if (alive) {
                        val old = mongoClient
                        mongoClient = fresh
                        runCatching { old.close() }
                        printLog("[MongoFactory] Reconnected to MongoDB", true)
                        break
                    }
                    delay(5000.milliseconds)
                }
            } finally {
                reconnecting.set(false)
            }
        }
    }

    suspend fun <T> transactionExecute(transactionName: String = "", body: suspend (ClientSession) -> T): T {
        mongoClient.startSession().use { session ->
            printLog("[TR::start::${session.hashCode()}] $transactionName ", true)
            session.startTransaction()
            val hooks = TransactionHooks()
            // Команда по Idempotency-Key: метка исполнения коммитится вместе с изменением
            coroutineContext[CommandKey]?.let { command -> hooks.beforeCommit(command) { command.commit(it) } }
            try {
                val result = withContext(hooks) { body(session).also { hooks.runBeforeCommit(session) } }
                if (session.hasActiveTransaction()) {
                    printLog("[TR::commit${session.hashCode()}] $transactionName ", true)
                    commitWithRetry(session)
                }
                // Кеши и журнал изменений узнают о правке только теперь: откат выше их не касается.
                hooks.runAfterCommit()
                return result
            } catch (e: Exception) {
                if (session.hasActiveTransaction()) {
                    printLog("[TR::abort${session.hashCode()}] $transactionName ", true)
                    runCatching { session.abortTransaction() }
                }
                // Временный сбой базы (1.53.0) - 503 и повтор клиента, а не 500 и не бизнес-отказ
                throw if (e is com.mongodb.MongoException) MongoErrors.translate(transactionName.ifBlank { "transaction" }, e) else e
            }
        }
    }

    /** Коммит с повтором (1.53.0): исход неизвестен (сеть оборвалась на ответе) - драйвер разрешает повторить сам коммит. */
    private suspend fun commitWithRetry(session: ClientSession) {
        repeat(COMMIT_TRIES - 1) {
            try {
                return session.commitTransaction()
            } catch (e: com.mongodb.MongoException) {
                if (!e.hasErrorLabel(com.mongodb.MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)) throw e
                printLog("[TR::commit-retry] ${e.message}", true)
                delay(50.milliseconds * (it + 1))
            }
        }
        session.commitTransaction()
    }

    private const val COMMIT_TRIES = 3
}

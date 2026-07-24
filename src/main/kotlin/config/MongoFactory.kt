package config

import MONGO_DB
import MONGO_URI
import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoClient
import extensions.printLog
import org.bson.codecs.configuration.CodecRegistries

object MongoFactory {
    private val client: MongoClient = createMongoClient()
    val db = client.getDatabase(MONGO_DB)

    private fun createMongoClient(connectionString: String = "$MONGO_URI/$MONGO_DB"): MongoClient {
        val codecRegistry = CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry()
        )

        val settings = MongoClientSettings.builder()
            .applyConnectionString(com.mongodb.ConnectionString(connectionString))
            .codecRegistry(codecRegistry)
            .build()

        return MongoClient.create(settings)
    }

    suspend fun <T> transactionExecute(transactionName: String = "", body: suspend (ClientSession) -> T): T {
        client.startSession().use { session ->
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
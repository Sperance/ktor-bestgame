package config

import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoClient
import extensions.printLog
import org.bson.codecs.configuration.CodecRegistries

object MongoFactory {
    private val client: MongoClient = createMongoClient()
    val db = client.getDatabase("my_first_project")

    private fun createMongoClient(connectionString: String = "mongodb://localhost:27017/my_first_project"): MongoClient {
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
            printLog("[TR::start::${session.hashCode()}] $transactionName ")
            session.startTransaction()
            try {
                val result = body(session)
                if (session.hasActiveTransaction()) {
                    printLog("[TR::commit${session.hashCode()}] $transactionName ")
                    session.commitTransaction()
                }
                return result
            } catch (e: Exception) {
                if (session.hasActiveTransaction()) {
                    printLog("[TR::abort${session.hashCode()}] $transactionName ")
                    session.abortTransaction()
                }
                throw e
            }
        }
    }
}
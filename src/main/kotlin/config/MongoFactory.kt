package config

import com.mongodb.MongoClientSettings
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoClient
import extensions.LocalDateTimeCodec
import extensions.printLog
import features.user.UserRepository
import org.bson.codecs.configuration.CodecRegistries

val repository_user = UserRepository()

object MongoFactory {
    private val client: MongoClient = createMongoClient()
    val db = client.getDatabase("my_first_project")

    init {
        printLog("***** MongoDB Connected! *****")
    }

    private fun createMongoClient(connectionString: String = "mongodb://localhost:27017/my_first_project"): MongoClient {
        val codecRegistry = CodecRegistries.fromRegistries(
            CodecRegistries.fromCodecs(LocalDateTimeCodec()),
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
            printLog("[TR::start] $transactionName ${session.hashCode()}")
            session.startTransaction()
            try {
                val result = body(session)
                if (session.hasActiveTransaction()) {
                    printLog("[TR::commit] $transactionName ${session.hashCode()}")
                    session.commitTransaction()
                }
                return result
            } catch (e: Exception) {
                if (session.hasActiveTransaction()) {
                    printLog("[TR::abort] $transactionName ${session.hashCode()}")
                    session.abortTransaction()
                }
                throw e
            }
        }
    }
}
package mongo_test

import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoClient

object MongoService {
    private const val uri = "mongodb://localhost:27017/my_first_project"
    private val client = MongoClient.create(uri)
    val db = client.getDatabase("my_first_project")

    suspend fun <T> transactionExecute(body: suspend (ClientSession) -> T): T {
        client.startSession().use { session ->
            println("[TRANSACTION::STR] ${session.hashCode()}")
            session.startTransaction()
            try {
                val result = body(session)
                if (session.hasActiveTransaction()) {
                    println("[TRANSACTION::CMT] ${session.hashCode()}")
                    session.commitTransaction()
                }
                return result
            } catch (e: Exception) {
                if (session.hasActiveTransaction()) {
                    println("[TRANSACTION::ABR] ${session.hashCode()}")
                    session.abortTransaction()
                }
                throw e
            }
        }
    }
}
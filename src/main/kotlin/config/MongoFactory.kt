package config

import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoClient
import extensions.printLog
import features.user.UserRepository

val repository_user = UserRepository()

object MongoFactory {
    private val client = MongoClient.create("mongodb://localhost:27017/my_first_project")
    val db = client.getDatabase("my_first_project")

    init {
        printLog("***** MongoDB Connected! *****")
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
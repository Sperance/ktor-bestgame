package ru.descend.infrastructure.http

import java.util.concurrent.TimeUnit
import org.bson.Document
import ru.descend.infrastructure.mongo.MongoFactory
import ru.descend.shared.MONGO_DB
import ru.descend.shared.MONGO_URI
import ru.descend.shared.extensions.printLog

/** Startup fails fast and explains itself: seeding and every write path need a reachable replica set. */
suspend fun verifyMongoConnection() {
    val database = MongoFactory.getDatabase().withTimeout(10, TimeUnit.SECONDS)
    val hello = try {
        database.runCommand(Document("hello", 1))
    } catch (cause: Exception) {
        throw startupFailure(
            "MongoDB at $MONGO_URI is unreachable, so the server cannot start. " +
                "Start MongoDB or correct MONGO_URI (current database: $MONGO_DB).",
            cause
        )
    }
    val topology = transactionCapableTopology(hello)
    if (topology == null) {
        throw startupFailure(
            "MongoDB at $MONGO_URI is a standalone server, but transactions are required by seeding " +
                "and every write. Run a replica set and point MONGO_URI at it, " +
                "for example mongodb://localhost:27017/?replicaSet=rs0.",
            null
        )
    }
    printLog("✅ MongoDB connected: database '$MONGO_DB' on $topology")
}

/** Replica set members report `setName` and mongos reports `msg`; standalone servers report neither. */
internal fun transactionCapableTopology(hello: Document): String? = hello.getString("setName") ?: hello.getString("msg")

private fun startupFailure(message: String, cause: Exception?): IllegalStateException {
    printLog("❌ $message", true)
    return IllegalStateException(message, cause)
}

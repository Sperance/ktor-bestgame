package ru.descend.infrastructure.http

import org.bson.Document
import ru.descend.infrastructure.mongo.MongoFactory
import ru.descend.shared.extensions.printLog

suspend fun configureMongoClient() {
    try {
        val ping = MongoFactory.getDatabase().runCommand(Document("ping", 1))
        if (ping.getDouble("ok") == 1.0) {
            printLog("✅ MongoDB connected successfully")
        }
    } catch (e: Exception) {
        e.printStackTrace()
        printLog("❌ Failed to connect to MongoDB")
    }
}

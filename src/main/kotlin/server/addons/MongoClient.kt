package server.addons

import config.MongoFactory
import extensions.printLog
import org.bson.Document

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
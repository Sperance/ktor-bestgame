package config

import MONGO_DB
import WIPE_DB
import SERVER_VERSION
import com.mongodb.client.model.Filters
import extensions.printLog
import kotlinx.coroutines.flow.firstOrNull
import org.bson.Document
import java.util.Date

/**
 * Разовая полная очистка базы при старте.
 *
 * Первый старт версии с новой меткой [MARKER] сносит базу целиком - вместе с аккаунтами - и
 * оставляет метку в коллекции [COLLECTION], поэтому следующие старты базу не трогают. Схема данных
 * меняется без переходников: несовместимая правка - новая метка и чистая база.
 */
object DatabaseWipe {

    /** Коллекция меток разовых операций над базой. */
    const val COLLECTION = "Migration"

    /** Метка последней очистки. Новая очистка - новая метка. */
    const val MARKER = "wipe-1.46.0"

    /** Сносит базу, если метки ещё нет. Зовётся до индексов и до первой транзакции сидера. */
    suspend fun runOnce() {
        val database = MongoFactory.getDatabase()
        val markers = database.getCollection(COLLECTION, Document::class.java)
        if (markers.find(Filters.eq("_id", MARKER)).firstOrNull() != null) return
        // Без флага (1.53.0) база остаётся: ошибка в MONGO_DB или восстановление из бэкапа без коллекции меток не теряет игроков
        if (WIPE_DB != MARKER) {
            printLog("Database wipe $MARKER skipped: WIPE_DB is not \"$MARKER\", $MONGO_DB kept as is")
            markers.insertOne(Document("_id", MARKER).append("server", SERVER_VERSION).append("at", Date()).append("kept", true))
            return
        }

        printLog("Database wipe $MARKER: dropping $MONGO_DB")
        database.drop()
        markers.insertOne(Document("_id", MARKER).append("server", SERVER_VERSION).append("at", Date()))
        printLog("  → database $MONGO_DB dropped, marker $MARKER written")
    }
}

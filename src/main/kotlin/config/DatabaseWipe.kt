package config

import MONGO_DB
import SERVER_VERSION
import com.mongodb.client.model.Filters
import extensions.printLog
import kotlinx.coroutines.flow.firstOrNull
import org.bson.Document
import java.util.Date

/**
 * Разовая полная очистка базы при старте (с 0.56.0).
 *
 * Пулы переехали из записей в свою коллекцию, тиры - внутрь описаний модификаторов, а предметы
 * ссылаются на модификаторы кодом: старые документы в новый формат не переводятся, база
 * начинается заново. Первый старт версии сносит базу целиком - вместе с аккаунтами - и оставляет
 * метку [MARKER] в коллекции [COLLECTION], поэтому следующие старты базу уже не трогают. Дальше
 * обычный сидер наполняет справочники, а администратора и тестового игрока заводит из окружения.
 *
 * Вторая очистка (0.66.3) - под формат 0.66.0: модификаторы стали семействами с вариантами, коды
 * `LOCAL_*`, `CRAFTED_*`, `IMPLICIT_*`, `GLOBAL_*` исчезли, а седьмая профессия дала инструмент,
 * которого у старых героев нет.
 */
object DatabaseWipe {

    /** Коллекция меток разовых операций над базой. */
    const val COLLECTION = "Migration"

    /** Метка последней очистки (0.56.0, затем формат 0.66.0). Новая очистка - новая метка, старая просто остаётся в коллекции. */
    const val MARKER = "wipe-0.66.0"

    /** Сносит базу, если метки ещё нет. Зовётся до индексов и до первой транзакции сидера. */
    suspend fun runOnce() {
        val database = MongoFactory.getDatabase()
        val markers = database.getCollection(COLLECTION, Document::class.java)
        if (markers.find(Filters.eq("_id", MARKER)).firstOrNull() != null) return

        printLog("Database wipe $MARKER: dropping $MONGO_DB")
        database.drop()
        markers.insertOne(Document("_id", MARKER).append("server", SERVER_VERSION).append("at", Date()))
        printLog("  → database $MONGO_DB dropped, marker $MARKER written")
    }
}

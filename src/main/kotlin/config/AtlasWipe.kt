package config

import SERVER_VERSION
import com.mongodb.client.model.Filters
import extensions.now
import extensions.printLog
import features.data.hero.Hero
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.LocalDateTime
import org.bson.Document
import java.util.Date

/**
 * Разовый бесплатный сброс Атласа при старте (1.41.0): Атлас переделан целиком - 301 узел в 10 ветвях, прежних кодов
 * узлов больше нет. У каждого героя снимаются взятые узлы, заработанные очки остаются. Метка [MARKER] - как у [DatabaseWipe].
 */
object AtlasWipe {

    /** Метка сброса Атласа. Новый сброс - новая метка. */
    const val MARKER = "atlas-wipe-1.41.0"

    /** Сбрасывает Атласы, если метки ещё нет. Зовётся после [CraftShift] и до индексов. */
    suspend fun runOnce() {
        val database = MongoFactory.getDatabase()
        val markers = database.getCollection(DatabaseWipe.COLLECTION, Document::class.java)
        if (markers.find(Filters.eq("_id", MARKER)).firstOrNull() != null) return

        printLog("Atlas wipe $MARKER")
        val heroes = database.getCollection(Hero::class.simpleName!!, Hero::class.java)
        var count = 0
        heroes.find().collect { hero ->
            hero.atlas = mutableListOf()
            hero.version += 1
            hero.updatedAt = LocalDateTime.now()
            heroes.replaceOne(Filters.eq("_id", hero._id), hero)
            count++
        }
        markers.insertOne(Document("_id", MARKER).append("server", SERVER_VERSION).append("at", Date()))
        printLog("  → $count atlases cleared, marker $MARKER written")
    }
}

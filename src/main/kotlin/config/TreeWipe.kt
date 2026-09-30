package config

import SERVER_VERSION
import com.mongodb.client.model.Filters
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.TakenNode
import extensions.now
import extensions.printLog
import features.data.hero.Hero
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.LocalDateTime
import org.bson.Document
import java.util.Date

/**
 * Разовый бесплатный сброс дерева навыков при старте (1.37.0): дерево разложено заново без наложений связей, и часть
 * связей сменилась - взятое у прежних героев могло оторваться от старта. У каждого героя остаётся только стартовый узел
 * класса, все очки возвращаются, самоцветы из гнёзд дерева уходят в тайник. Сферы сожаления не тратятся.
 * Метка [MARKER] в коллекции [DatabaseWipe.COLLECTION] - как у [DatabaseWipe].
 */
object TreeWipe {

    /** Метка сброса дерева. Новый сброс - новая метка. */
    const val MARKER = "tree-wipe-1.37.0"

    /** Сбрасывает деревья, если метки ещё нет. Зовётся после [ItemWipe] и до индексов. */
    suspend fun runOnce(index: ContentIndex) {
        val database = MongoFactory.getDatabase()
        val markers = database.getCollection(DatabaseWipe.COLLECTION, Document::class.java)
        if (markers.find(Filters.eq("_id", MARKER)).firstOrNull() != null) return

        printLog("Tree wipe $MARKER")
        val heroes = database.getCollection(Hero::class.simpleName!!, Hero::class.java)
        var count = 0
        heroes.find().collect { hero ->
            hero.tree = index.heroClass(hero.heroClass)?.startNode?.let { mutableListOf(TakenNode(it)) } ?: mutableListOf()
            hero.items.forEach { it.socket = null }
            hero.version += 1
            hero.updatedAt = LocalDateTime.now()
            heroes.replaceOne(Filters.eq("_id", hero._id), hero)
            count++
        }
        markers.insertOne(Document("_id", MARKER).append("server", SERVER_VERSION).append("at", Date()))
        printLog("  → $count heroes back on their start node, marker $MARKER written")
    }
}

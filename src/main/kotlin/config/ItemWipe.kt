package config

import SERVER_VERSION
import com.mongodb.client.model.Filters
import extensions.now
import extensions.printLog
import features.data.auction.AuctionLot
import features.data.hero.Hero
import features.data.hero.Starter
import com.sperance.exileforge.rules.content.ContentIndex
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.LocalDateTime
import org.bson.Document
import java.util.Date

/**
 * Разовая очистка предметов при старте (1.33.0): тиры аффиксов пересчитаны под уровень предмета 1–85, и номера тиров
 * прежних копий больше не совпадают с описаниями. Аккаунты и герои остаются: у каждого героя снимаются вещи и витрина
 * торговца, выдаётся стартовый набор класса, лоты аукциона сносятся целиком.
 * Метка [MARKER] в коллекции [DatabaseWipe.COLLECTION] - как у [DatabaseWipe].
 */
object ItemWipe {

    /** Метка очистки предметов. Новая очистка - новая метка. */
    const val MARKER = "item-wipe-1.33.0"

    /** Сносит вещи, если метки ещё нет. Зовётся после [GuildWipe] и до индексов. */
    suspend fun runOnce(index: ContentIndex) {
        val database = MongoFactory.getDatabase()
        val markers = database.getCollection(DatabaseWipe.COLLECTION, Document::class.java)
        if (markers.find(Filters.eq("_id", MARKER)).firstOrNull() != null) return

        printLog("Item wipe $MARKER")
        database.getCollection(AuctionLot::class.simpleName!!, Document::class.java).drop()
        val heroes = database.getCollection(Hero::class.simpleName!!, Hero::class.java)
        var count = 0
        heroes.find().collect { hero ->
            hero.items.clear()
            hero.merchant = null
            index.heroClass(hero.heroClass)?.let { Starter.grant(hero, index, it) }
            hero.version += 1
            hero.updatedAt = LocalDateTime.now()
            heroes.replaceOne(Filters.eq("_id", hero._id), hero)
            count++
        }
        markers.insertOne(Document("_id", MARKER).append("server", SERVER_VERSION).append("at", Date()))
        printLog("  → lots dropped, $count heroes re-geared, marker $MARKER written")
    }
}

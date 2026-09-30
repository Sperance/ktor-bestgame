package config

import SERVER_VERSION
import com.mongodb.client.model.Filters
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.roll.ItemInstance
import extensions.now
import extensions.printLog
import features.data.auction.AuctionLot
import features.data.hero.Hero
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.LocalDateTime
import org.bson.Document
import java.util.Date

/**
 * Разовый сдвиг номеров тиров при старте (1.40.0): у семейств модификаторов появилась вершина сетки уровня 92, она стала
 * тиром 1, и прежние тиры сдвинулись на номер вниз. Копия хранит номер тира, а не числа, поэтому каждому роллу такого
 * семейства номер прибавляется на единицу - значения вещей не меняются. Вещи героев, переполнение, лоты аукциона;
 * витрина торговца просто обновится. Метка [MARKER] в коллекции [DatabaseWipe.COLLECTION] - как у [DatabaseWipe].
 */
object TierShift {

    /** Метка сдвига тиров. Новый сдвиг - новая метка. */
    const val MARKER = "tier-shift-1.40.0"

    fun shift(item: ItemInstance, index: ContentIndex) {
        item.rolls = item.rolls.map { roll ->
            // Вершины верстака (1.41.0) сдвигает свой [CraftShift]: здесь только тиры аффиксов 1.40.0.
            val crowned = roll.tier > 0 && index.modifier(roll.code)?.let { !it.crafted && it.tiers.firstOrNull()?.apex == true } == true
            if (crowned) roll.copy(tier = roll.tier + 1) else roll
        }
    }

    /** Сдвигает тиры, если метки ещё нет. Зовётся после [TreeWipe] и до индексов. */
    suspend fun runOnce(index: ContentIndex) {
        val database = MongoFactory.getDatabase()
        val markers = database.getCollection(DatabaseWipe.COLLECTION, Document::class.java)
        if (markers.find(Filters.eq("_id", MARKER)).firstOrNull() != null) return

        printLog("Tier shift $MARKER")
        val heroes = database.getCollection(Hero::class.simpleName!!, Hero::class.java)
        var count = 0
        heroes.find().collect { hero ->
            (hero.items + hero.overflow).forEach { shift(it, index) }
            hero.merchant = null
            hero.version += 1
            hero.updatedAt = LocalDateTime.now()
            heroes.replaceOne(Filters.eq("_id", hero._id), hero)
            count++
        }
        val lots = database.getCollection(AuctionLot::class.simpleName!!, AuctionLot::class.java)
        var shifted = 0
        lots.find().collect { lot ->
            lot.equipment?.let { shift(it, index); lots.replaceOne(Filters.eq("_id", lot._id), lot); shifted++ }
        }
        markers.insertOne(Document("_id", MARKER).append("server", SERVER_VERSION).append("at", Date()))
        printLog("  → $count heroes and $shifted lots shifted, marker $MARKER written")
    }
}

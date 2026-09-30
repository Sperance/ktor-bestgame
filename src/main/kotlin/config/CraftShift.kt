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
 * Разовый сдвиг тиров верстака при старте (1.41.0): у рецептов появился тир 1 уровней 71–100 (вершина сетки CRAFTED),
 * прежние рецепты сдвинулись на номер вниз. Ролл верстака на вещи и известный герою рецепт (`<код>_T<n>`) получают номер
 * на единицу больше - числа и знания героя не меняются. Метка [MARKER] в коллекции [DatabaseWipe.COLLECTION].
 */
object CraftShift {

    /** Метка сдвига верстака. Новый сдвиг - новая метка. */
    const val MARKER = "craft-shift-1.41.0"

    private fun crowned(code: String, index: ContentIndex): Boolean =
        index.modifier(code)?.let { it.crafted && it.tiers.firstOrNull()?.apex == true } == true

    fun shift(item: ItemInstance, index: ContentIndex) {
        item.rolls = item.rolls.map { roll -> if (roll.tier > 0 && crowned(roll.code, index)) roll.copy(tier = roll.tier + 1) else roll }
    }

    /** Рецепт `<код>_T<n>` сдвинутого описания - `<код>_T<n+1>`; прочие как есть. */
    fun recipe(code: String, index: ContentIndex): String {
        val at = code.lastIndexOf("_T").takeIf { it > 0 } ?: return code
        val tier = code.substring(at + 2).toIntOrNull() ?: return code
        return if (crowned(code.substring(0, at), index)) "${code.substring(0, at)}_T${tier + 1}" else code
    }

    /** Сдвигает, если метки ещё нет. Зовётся после [TierShift] и до индексов. */
    suspend fun runOnce(index: ContentIndex) {
        val database = MongoFactory.getDatabase()
        val markers = database.getCollection(DatabaseWipe.COLLECTION, Document::class.java)
        if (markers.find(Filters.eq("_id", MARKER)).firstOrNull() != null) return

        printLog("Craft shift $MARKER")
        val heroes = database.getCollection(Hero::class.simpleName!!, Hero::class.java)
        var count = 0
        heroes.find().collect { hero ->
            (hero.items + hero.overflow).forEach { shift(it, index) }
            hero.recipes = hero.recipes.map { recipe(it, index) }.toMutableList()
            hero.version += 1
            hero.updatedAt = LocalDateTime.now()
            heroes.replaceOne(Filters.eq("_id", hero._id), hero)
            count++
        }
        val lots = database.getCollection(AuctionLot::class.simpleName!!, AuctionLot::class.java)
        lots.find().collect { lot -> lot.equipment?.let { shift(it, index); lots.replaceOne(Filters.eq("_id", lot._id), lot) } }
        markers.insertOne(Document("_id", MARKER).append("server", SERVER_VERSION).append("at", Date()))
        printLog("  → $count heroes shifted, marker $MARKER written")
    }
}

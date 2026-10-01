package features.data.heroStats

import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.sperance.exileforge.rules.content.Stat
import com.sperance.exileforge.rules.content.StatTally
import config.MongoFactory
import extensions.printLog
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable
import org.bson.Document

/** Статистика героя, как её отдаёт `GET hero/stats`: ключ - значение, только ненулевое. */
@Serializable
data class HeroStatsView(val values: Map<String, Long> = emptyMap())

/**
 * Статистика героя (1.49.0): коллекция `HeroStats`, документ на героя - `{_id: heroId, v: {ключ: число}}`. Пишется одним
 * атомарным обновлением после записи героя (`$inc` сумм, `$max`/`$min` рекордов), без версии: статистика не решает
 * ничего, что проверяла бы транзакция героя, а потерянный прирост - лишь недосчитанная строка летописи.
 */
object HeroStatsStore {
    const val COLLECTION = "HeroStats"
    private const val VALUES = "v"

    private val collection: MongoCollection<Document> by lazy { MongoFactory.getDatabase().getCollection(COLLECTION, Document::class.java) }

    /** Прирост [tally] героя [heroId]; пустой не пишется. Ошибка записи не роняет команду - только лог. */
    suspend fun bump(heroId: String, tally: StatTally) {
        if (tally.empty) return
        val updates = tally.sums.map { (key, value) -> Updates.inc("$VALUES.$key", value) } +
            tally.highs.map { (key, value) -> Updates.max("$VALUES.$key", value) } +
            tally.lows.map { (key, value) -> Updates.min("$VALUES.$key", value) }
        tally.clear()
        runCatching { collection.updateOne(Filters.eq("_id", heroId), Updates.combine(updates), UpdateOptions().upsert(true)) }
            .onFailure { printLog("[HeroStats] ❌ $heroId: ${it.message}", true) }
    }

    /** Статистика героя [heroId]; строки, дублирующие летопись, - из его [counters], а не из коллекции. */
    suspend fun read(heroId: String, counters: Map<String, Long>): HeroStatsView {
        val values = collection.find(Filters.eq("_id", heroId)).firstOrNull()?.get(VALUES, Document::class.java)
        val stored = values?.entries?.mapNotNull { (key, value) ->
            if (Stat.derived(key)) null else (value as? Number)?.toLong()?.takeIf { it != 0L }?.let { key to it }
        }?.toMap().orEmpty()
        return HeroStatsView(stored + Stat.fromCounters(counters))
    }
}

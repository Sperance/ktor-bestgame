package features.data.routeTiming

import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoCollection
import config.MongoFactory
import extensions.printLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.bson.Document
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray

/**
 * Замеры маршрутов (1.52.0): коллекция `RouteTiming`, документ на маршрут за сутки (UTC) - `{_id: "<день>|<метод путь>", day, route,
 * count, totalMs, maxMs, errors, buckets: {"<=10": n, …}}`. Ответы копятся в памяти и раз в минуту уходят в Mongo атомарными
 * `$inc`/`$max`; среднее - totalMs / count, p95 читается по корзинам. Id в пути (24 hex, числа) сводятся к `{id}`.
 */
object RouteTimings {
    private const val COLLECTION = "RouteTiming"
    private const val FLUSH_MS = 60_000L
    /** Верхние границы корзин, мс; последняя - всё, что дольше. */
    val BUCKETS = longArrayOf(10, 25, 50, 100, 250, 500, 1000, 2500)
    private val ID = Regex("/([0-9a-fA-F]{24}|\\d+)(?=/|$)")

    private class Tally {
        val count = AtomicLong(); val total = AtomicLong(); val errors = AtomicLong()
        val max = AtomicLong(); val buckets = AtomicLongArray(BUCKETS.size + 1)
    }

    private val tallies = ConcurrentHashMap<String, Tally>()
    private val collection: MongoCollection<Document> by lazy { MongoFactory.getDatabase().getCollection(COLLECTION, Document::class.java) }

    /** Один ответ: метод, путь, сколько шёл и с каким статусом. */
    fun record(method: String, path: String, millis: Long, status: Int) {
        val key = "$method ${ID.replace(path, "/{id}")}"
        val tally = tallies.computeIfAbsent(key) { Tally() }
        tally.count.incrementAndGet()
        tally.total.addAndGet(millis)
        tally.max.accumulateAndGet(millis, ::maxOf)
        if (status >= 500) tally.errors.incrementAndGet()
        tally.buckets.incrementAndGet(BUCKETS.indexOfFirst { millis <= it }.let { if (it < 0) BUCKETS.size else it })
    }

    fun start(scope: CoroutineScope) = scope.launch {
        while (isActive) {
            delay(FLUSH_MS)
            runCatching { flush() }.onFailure { printLog("[RouteTiming] ❌ ${it.message}", true) }
        }
    }

    private suspend fun flush() {
        val day = LocalDate.now(ZoneOffset.UTC).toString()
        tallies.keys.toList().forEach { route ->
            val tally = tallies.remove(route) ?: return@forEach
            val updates = mutableListOf(
                Updates.set("day", day), Updates.set("route", route),
                Updates.inc("count", tally.count.get()), Updates.inc("totalMs", tally.total.get()), Updates.inc("errors", tally.errors.get()),
                Updates.max("maxMs", tally.max.get()),
            )
            (0..BUCKETS.size).forEach { i ->
                val n = tally.buckets.get(i)
                if (n > 0) updates += Updates.inc("buckets.${if (i < BUCKETS.size) "le${BUCKETS[i]}" else "gt${BUCKETS.last()}"}", n)
            }
            collection.updateOne(Filters.eq("_id", "$day|$route"), Updates.combine(updates), UpdateOptions().upsert(true))
        }
    }
}

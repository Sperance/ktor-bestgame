package features.logic.pools

import application.enums.EnumInfluence
import extensions.weightedRandomExt
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Запись, которую можно вытянуть из пула (с 0.39.0): модификатор предмета или монстра,
 * шаблон экипировки, уникалка.
 *
 * Пул - это тег, и реестра пулов нет: запись сама говорит, в каких пулах она состоит и с каким
 * весом, а источник (шаблон, таблица добычи, босс, сфера, ремесло) называет пулы, из которых
 * тянет. Новый пул - это новый тег на записях и его имя у источника, без строчки кода.
 */
interface Pooled {
    val pools: Map<String, Int>
}

/** Запись пула с её весом в этой тяге. */
data class Weighted<T>(val value: T, val weight: Int)

object Pools {
    /** Разделитель частей тега: `local:armor`, `influence:SHAPER`, `boss:BOSS_TIDECALLER`. */
    const val SEPARATOR = ":"

    /**
     * Вес записи в тяге по [tags], как `spawn_weights` в PoE: решает первый тег источника,
     * в котором запись состоит, и вес 0 в нём исключает её, даже если дальше она есть с весом.
     */
    fun weight(entry: Pooled, tags: List<String>): Int = tags.firstNotNullOfOrNull { entry.pools[it] } ?: 0

    /** Пул по тегам источника: записи с положительным весом, в исходном порядке. */
    fun <T : Pooled> of(entries: Iterable<T>, tags: List<String>): List<Weighted<T>> =
        entries.mapNotNull { entry -> weight(entry, tags).takeIf { it > 0 }?.let { Weighted(entry, it) } }

    /** Одна запись пула по весам. */
    fun <T> draw(pool: List<Weighted<T>>, random: Random): T? {
        var point = random.nextInt(pool.sumOf { it.weight }.coerceAtLeast(1))
        pool.forEach { entry -> point -= entry.weight; if (point < 0) return entry.value }
        return null
    }

    /** Одна запись пула по весам на общем генераторе сервера. */
    fun <T> draw(pool: List<Weighted<T>>): T? = pool.weightedRandomExt { it.weight }?.value

    /** Пул модификаторов, который открывает предмету его влияние. */
    fun influence(influence: EnumInfluence): String = "influence$SEPARATOR${influence.name}"

    /**
     * Пулы одного набора записей по тегам источника (с 0.49.0): каждая тяга по одним и тем же
     * тегам собирается один раз, дальше отдаётся готовой. Живёт внутри снимка кеша и умирает с ним.
     */
    class Index<T : Pooled>(private val entries: List<T>) {
        private val byTags = ConcurrentHashMap<List<String>, List<Weighted<T>>>()

        fun of(tags: List<String>): List<Weighted<T>> = byTags.getOrPut(tags) { Pools.of(entries, tags) }
    }
}

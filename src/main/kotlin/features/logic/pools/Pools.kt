package features.logic.pools

import application.enums.EnumInfluence
import extensions.weightedRandomExt
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Запись, которую можно вытянуть из пула: модификатор предмета или монстра, шаблон экипировки.
 *
 * С 0.56.0 запись о пулах не знает ничего - только свой стабильный код. Кто в каком пуле и с каким
 * весом, лежит в коллекции [Pool] (сид - `content/pools.json`), а источник (шаблон, таблица добычи,
 * босс, сфера, ремесло) по-прежнему называет теги пулов, из которых тянет.
 */
interface Pooled {
    val code: String
}

/** Запись пула с её весом в этой тяге. */
data class Weighted<T>(val value: T, val weight: Int)

/**
 * Пулы одного справочника ([EnumPoolTarget]), сведённые по тегу: `тег -> код -> вес`.
 *
 * Разрешение как `spawn_weights` в PoE: источник перечисляет теги по приоритету, вес записи даёт
 * первый тег, в пуле которого она есть, и вес 0 в нём исключает её, даже если дальше она есть с весом.
 */
class PoolTable(pools: Collection<Pool>) {
    private val byTag: Map<String, Map<String, Int>> =
        pools.groupBy { it.code }.mapValues { (_, same) -> same.fold(emptyMap()) { merged, pool -> merged + pool.entries } }

    /** Все теги таблицы. */
    val tags: Set<String> get() = byTag.keys

    /** Состав пула [tag]: код -> вес. */
    fun members(tag: String): Map<String, Int> = byTag[tag].orEmpty()

    /** Вес записи [code] в тяге по [tags]. */
    fun weight(code: String, tags: List<String>): Int = tags.firstNotNullOfOrNull { byTag[it]?.get(code) } ?: 0

    /** Пул по тегам источника: записи с положительным весом, в исходном порядке. */
    fun <T : Pooled> of(entries: Iterable<T>, tags: List<String>): List<Weighted<T>> =
        entries.mapNotNull { entry -> weight(entry.code, tags).takeIf { it > 0 }?.let { Weighted(entry, it) } }

    /**
     * Тяги одного набора записей по этой таблице (с 0.49.0): каждая по одним и тем же тегам
     * собирается один раз, дальше отдаётся готовой. Живёт внутри ревизии кеша и умирает с ней.
     */
    fun <T : Pooled> index(entries: List<T>): Index<T> = Index(entries, this)

    class Index<T : Pooled>(private val entries: List<T>, private val table: PoolTable) {
        private val byTags = ConcurrentHashMap<List<String>, List<Weighted<T>>>()

        fun of(tags: List<String>): List<Weighted<T>> = byTags.getOrPut(tags) { table.of(entries, tags) }
    }

    companion object {
        val EMPTY = PoolTable(emptyList())

        /** Таблицы всех справочников по набору пулов. */
        fun byTarget(pools: Collection<Pool>): Map<EnumPoolTarget, PoolTable> =
            EnumPoolTarget.entries.associateWith { target -> PoolTable(pools.filter { it.kind.target == target }) }
    }
}

object Pools {
    /** Разделитель частей тега: `local:armor`, `influence:SHAPER`, `boss:BOSS_TIDECALLER`. */
    const val SEPARATOR = ":"

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
}

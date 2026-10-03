package com.sperance.exileforge.rules.table

import com.sperance.exileforge.rules.fail
import com.sperance.exileforge.rules.roll.Dice
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

/** Чьи коды лежат в таблице: описания модификаторов, шаблоны, предметы сумки, монстры, голые значения или строки добычи. */
@Serializable
enum class TableKind { MODIFIER, TEMPLATE, ITEM, MONSTER, VALUE, LOOT }

/**
 * Запись таблицы. В тяге ([Table.pick]) решает [weight]; в броске добычи ([Table.roll]) - [chance] на
 * каждую запись независимо и [amount] штук. [ref] - `вид:код` или голый код вида таблицы.
 */
@Serializable
data class TableEntry(
    val ref: String,
    val weight: Int = 100,
    val chance: Double? = null,
    val amount: List<Long>? = null,
    /** С какого уровня запись открыта; 0 - всегда. */
    val minLevel: Int = 0,
) {
    val kind: TableKind? get() = Ref.kind(ref)
    val code: String get() = Ref.code(ref)
}

/** Ссылка `вид:код`: `item:ORB_OF_ALCHEMY`, `table:drop`, `template:IRON_HAT`; без вида - вид таблицы. */
object Ref {
    const val TABLE = "table"
    private val kinds = TableKind.entries.associateBy { it.name.lowercase() }

    fun kind(ref: String): TableKind? = ref.substringBefore(':', "").let { kinds[it] }
    fun isTable(ref: String): Boolean = ref.startsWith("$TABLE:")
    fun code(ref: String): String = if (':' in ref && (kind(ref) != null || isTable(ref))) ref.substringAfter(':') else ref
    fun of(kind: TableKind, code: String): String = "${kind.name.lowercase()}:$code"
    fun table(tag: String): String = "$TABLE:$tag"
}

/**
 * Таблица (`tables.json`): один взвешенный выбор на всё - аффиксы, монстры, шаблоны, редкости,
 * сферы, добыча. [includes] - таблицы того же вида, чьи записи ложатся первыми, свои - поверх (вес 0
 * исключает унаследованную). [gold] есть только у добычи.
 */
@Serializable
data class Table(
    val tag: String,
    val kind: TableKind,
    val includes: List<String> = emptyList(),
    @Serializable(with = EntriesSerializer::class) val entries: List<TableEntry> = emptyList(),
    val gold: List<Long>? = null,
)

/** Записи как объект `код -> вес` (кратко) или как массив записей (с шансами и количеством). */
object EntriesSerializer : KSerializer<List<TableEntry>> {
    private val list = ListSerializer(TableEntry.serializer())
    override val descriptor: SerialDescriptor = SerialDescriptor("TableEntries", list.descriptor)

    override fun serialize(encoder: Encoder, value: List<TableEntry>) {
        val json = encoder as? JsonEncoder ?: return encoder.encodeSerializableValue(list, value)
        val plain = value.all { it.chance == null && it.amount == null && it.minLevel == 0 }
        if (plain) {
            json.encodeJsonElement(JsonObject(value.associate { it.ref to JsonPrimitive(it.weight) }))
        } else {
            json.encodeSerializableValue(list, value)
        }
    }

    override fun deserialize(decoder: Decoder): List<TableEntry> {
        val json = decoder as? JsonDecoder ?: return decoder.decodeSerializableValue(list)
        return when (val element = json.decodeJsonElement()) {
            is JsonObject -> element.map { (ref, weight) -> TableEntry(ref, weight.jsonPrimitive.int) }
            is JsonArray -> json.json.decodeFromJsonElement(list, element)
            else -> fail("table entries: object or array expected")
        }
    }
}

@Serializable
data class TablesFile(val tables: List<Table> = emptyList())

/** Запись тяги с её весом. */
data class Weighted<T>(val value: T, val weight: Int)

/**
 * Все таблицы, сведённые по тегу. Разрешение как `spawn_weights` PoE: источник перечисляет теги по
 * приоритету, вес записи даёт первый тег, где она есть, и вес 0 там исключает её.
 */
class TableSet(tables: Collection<Table>) {
    private val raw: Map<String, List<Table>> = tables.groupBy { it.tag }
    private val kinds: Map<String, TableKind> = raw.mapValues { (tag, same) ->
        same.map { it.kind }.distinct().singleOrNull() ?: fail("table $tag: mixed kinds")
    }
    private val resolved: Map<String, LinkedHashMap<String, TableEntry>> = raw.keys.associateWith { resolve(it, ArrayList()) }
    private val golds: Map<String, List<Long>> = raw.mapNotNull { (tag, same) -> same.firstNotNullOfOrNull { it.gold }?.let { tag to it } }.toMap()

    val tags: Set<String> get() = raw.keys

    fun kind(tag: String): TableKind? = kinds[tag]
    fun has(tag: String): Boolean = tag in raw

    /** Состав таблицы: код -> запись, унаследованное первым. */
    fun members(tag: String): Map<String, TableEntry> = resolved[tag].orEmpty()

    /** Вес кода в тяге по [tags]: первая таблица, где он есть. */
    fun weight(code: String, tags: List<String>): Int = tags.firstNotNullOfOrNull { resolved[it]?.get(code)?.weight } ?: 0

    /** Записи [candidates] с положительным весом по [tags], в порядке кандидатов. */
    fun <T> of(candidates: Iterable<T>, tags: List<String>, code: (T) -> String): List<Weighted<T>> = candidates.mapNotNull { candidate -> weight(code(candidate), tags).takeIf { it > 0 }?.let { Weighted(candidate, it) } }

    /** Все коды тяги по [tags] с весами: записи первого тега, потом невиданные из следующих. */
    fun pool(tags: List<String>, level: Int = Int.MAX_VALUE): List<Weighted<String>> {
        val seen = HashSet<String>()
        val out = ArrayList<Weighted<String>>()
        tags.forEach { tag ->
            resolved[tag]?.values?.forEach { entry ->
                if (seen.add(entry.code) && entry.weight > 0 && entry.minLevel <= level) out += Weighted(entry.code, entry.weight)
            }
        }
        return out
    }

    /** Записи таблицы добычи [tag] целиком - для броска по шансам; null, если такой нет. */
    fun loot(tag: String): List<TableEntry>? = resolved[tag]?.values?.toList()
    fun gold(tag: String): List<Long>? = golds[tag]

    private fun resolve(tag: String, path: MutableList<String>): LinkedHashMap<String, TableEntry> {
        if (tag in path) fail("tables include each other: ${path + tag}")
        val own = raw[tag] ?: fail("table ${path.lastOrNull()} includes unknown table $tag")
        path += tag
        val merged = LinkedHashMap<String, TableEntry>()
        own.flatMap { it.includes }.distinct().forEach { included ->
            if (kinds[included] != kinds[tag]) fail("table $tag includes $included of another kind")
            merged += resolve(included, path)
        }
        own.flatMap { it.entries }.forEach { entry -> merged[entry.code] = entry }
        path.removeAt(path.lastIndex)
        return merged
    }

    /** Ссылки на другие таблицы в записях: проверка, что каждая существует. */
    fun validateRefs(exists: (TableKind, String) -> Boolean) {
        raw.forEach { (tag, same) ->
            val kind = kinds.getValue(tag)
            same.flatMap { it.entries }.forEach { entry ->
                when {
                    Ref.isTable(entry.ref) -> if (entry.code !in raw) fail("table $tag: unknown table ${entry.code}")

                    kind == TableKind.LOOT -> {
                        val refKind = entry.kind ?: fail("table $tag: a loot entry names its kind, got ${entry.ref}")
                        if (!exists(refKind, entry.code)) fail("table $tag: unknown ${entry.ref}")
                        if (entry.chance == null || entry.chance !in 0.0..1.0) fail("table $tag: chance of ${entry.ref}")
                        entry.amount?.let { if (it.size != 2 || it[0] > it[1] || it[0] < 0) fail("table $tag: amount of ${entry.ref}") }
                    }

                    else -> if (!exists(entry.kind ?: kind, entry.code)) fail("table $tag: unknown ${entry.ref}")
                }
                if (entry.weight < 0) fail("table $tag: weight of ${entry.ref}")
            }
            same.forEach { table -> table.gold?.let { if (it.size != 2 || it[0] > it[1]) fail("table $tag: gold") } }
        }
    }
}

object Tables {
    /** Одна запись по весам; null у пустой тяги. */
    fun <T> draw(pool: List<Weighted<T>>, dice: Dice): T? {
        val total = pool.sumOf { it.weight.toLong() }
        if (total <= 0) return null
        var point = dice.nextLong(total)
        pool.forEach { entry ->
            point -= entry.weight
            if (point < 0) return entry.value
        }
        return pool.last().value
    }

    /** Одна запись по вещественным весам. */
    fun <T> draw(pool: List<T>, weight: (T) -> Double, dice: Dice): T? {
        val weights = pool.map { weight(it).coerceAtLeast(0.0) }
        val total = weights.sum()
        if (total <= 0.0) return null
        var point = dice.nextDouble() * total
        pool.forEachIndexed { index, entry ->
            point -= weights[index]
            if (point < 0) return entry
        }
        return pool.last()
    }

    /** Значение таблицы голых значений [tag] по весам, приведённое к перечислению. */
    inline fun <reified E : Enum<E>> value(tables: TableSet, tag: String, dice: Dice, crossinline boost: (E) -> Double = { 1.0 }): E? {
        val pool = tables.pool(listOf(tag)).mapNotNull { entry -> enumValues<E>().firstOrNull { it.name == entry.value }?.let { Weighted(it, entry.weight) } }
        return draw(pool, { (it.weight * boost(it.value)) }, dice)?.value
    }
}

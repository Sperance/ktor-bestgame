package config

import base.exception.model.PoolExceptions
import features.logic.pools.EnumPoolKind
import features.logic.pools.EnumPoolTarget
import features.logic.pools.Pool
import features.logic.pools.PoolRules
import features.logic.pools.PoolTable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Начальные данные коллекции `Pool` (с 0.56.0) - все пулы игры одним файлом `content/pools.json`:
 *
 * ```
 * { "<вид>": { "<тег>": { "<код записи>": <вес>, ... }, ... }, ... }
 * ```
 *
 * Вид ([EnumPoolKind]) говорит, чьи коды лежат в пуле: модификаторов предметов и монстров или
 * шаблонов экипировки (баз, уникалок, мифических предметов).
 *
 * С 0.66.0 пул может наследовать другие пулы своего вида:
 *
 * ```
 * "helmet": { "includes": ["armour"], "entries": { "<код>": <вес>, ... } }
 * ```
 *
 * Записи наследуемых пулов ложатся первыми в порядке перечисления, собственные - поверх, так что
 * свой вес перекрывает унаследованный, а вес 0 исключает унаследованную запись. В базу и клиенту
 * уходит уже развёрнутый плоский пул: наследование - удобство файла, а не правило игры.
 */
object PoolSeeder {

    const val FILE = "pools.json"
    private const val INCLUDES = "includes"
    private const val ENTRIES = "entries"

    private val json = Json { ignoreUnknownKeys = true }

    /** Пул до разворота: что наследует и что несёт сам. */
    private class Raw(val includes: List<String>, val entries: Map<String, Int>)

    /** Документы коллекции `Pool`. */
    val pools: List<Pool> by lazy {
        val document = json.parseToJsonElement(ContentResource.read(FILE)).jsonObject
        val pools = document.flatMap { (kindName, byTag) ->
            val kind = EnumPoolKind.entries.firstOrNull { it.name == kindName } ?: throw PoolExceptions.funException("PoolSeeder", "unknown kind $kindName")
            val raw = byTag.jsonObject.mapValues { (tag, value) -> parse(kind, tag, value) }
            raw.keys.map { tag -> Pool(code = tag, kind = kind, entries = resolve(kind, tag, raw, ArrayList())) }
        }
        pools.firstNotNullOfOrNull(PoolRules::problem)?.let { throw PoolExceptions.funException("PoolSeeder", it) }
        pools
    }

    private fun parse(kind: EnumPoolKind, tag: String, value: JsonElement): Raw {
        val body = value as? JsonObject ?: throw PoolExceptions.funException("PoolSeeder", "$kind $tag: not an object")
        val structured = body.containsKey(INCLUDES) || body.containsKey(ENTRIES)
        val includes = if (structured) (body[INCLUDES] as? JsonArray).orEmpty().map { it.jsonPrimitive.content } else emptyList()
        val entries = (if (structured) (body[ENTRIES] as? JsonObject).orEmpty() else body).mapValues { (code, weight) ->
            (weight as? JsonPrimitive)?.takeUnless { it.isString }?.int ?: throw PoolExceptions.funException("PoolSeeder", "$kind $tag: weight of $code")
        }
        return Raw(includes, entries)
    }

    /** Плоский состав пула: сначала унаследованное, поверх - своё. Цикл наследования - ошибка файла. */
    private fun resolve(kind: EnumPoolKind, tag: String, raw: Map<String, Raw>, path: MutableList<String>): Map<String, Int> {
        if (tag in path) throw PoolExceptions.funException("PoolSeeder", "$kind: pools include each other: ${path + tag}")
        val own = raw[tag] ?: throw PoolExceptions.funException("PoolSeeder", "$kind ${path.lastOrNull()}: includes unknown pool $tag")
        path += tag
        val merged = LinkedHashMap<String, Int>()
        own.includes.forEach { merged += resolve(kind, it, raw, path) }
        merged += own.entries
        path.removeAt(path.lastIndex)
        return merged
    }

    /** Таблицы пулов из файла - для проверок содержимого, которым не нужна база. */
    val tables: Map<EnumPoolTarget, PoolTable> by lazy { PoolTable.byTarget(pools) }

    fun table(target: EnumPoolTarget): PoolTable = tables.getValue(target)
}

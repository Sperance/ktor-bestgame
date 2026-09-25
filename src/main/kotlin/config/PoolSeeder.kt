package config

import base.exception.model.PoolExceptions
import features.logic.pools.EnumPoolKind
import features.logic.pools.EnumPoolTarget
import features.logic.pools.Pool
import features.logic.pools.PoolRules
import features.logic.pools.PoolTable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Начальные данные коллекции `Pool` (с 0.56.0) - все пулы игры одним файлом `content/pools.json`:
 *
 * ```
 * { "<вид>": { "<тег>": { "<код записи>": <вес>, ... }, ... }, ... }
 * ```
 *
 * Вид ([EnumPoolKind]) говорит, чьи коды лежат в пуле: модификаторов предметов, модификаторов
 * монстров или шаблонов экипировки (баз, уникалок, мифических предметов).
 */
object PoolSeeder {

    const val FILE = "pools.json"

    private val json = Json { ignoreUnknownKeys = true }

    private val document = MapSerializer(EnumPoolKind.serializer(), MapSerializer(String.serializer(), MapSerializer(String.serializer(), Int.serializer())))

    /** Документы коллекции `Pool`. */
    val pools: List<Pool> by lazy {
        val pools = json.decodeFromString(document, ContentResource.read(FILE)).flatMap { (kind, byTag) ->
            byTag.map { (tag, entries) -> Pool(code = tag, kind = kind, entries = entries) }
        }
        pools.firstNotNullOfOrNull(PoolRules::problem)?.let { throw PoolExceptions.funException("PoolSeeder", it) }
        pools
    }

    /** Таблицы пулов из файла - для проверок содержимого, которым не нужна база. */
    val tables: Map<EnumPoolTarget, PoolTable> by lazy { PoolTable.byTarget(pools) }

    fun table(target: EnumPoolTarget): PoolTable = tables.getValue(target)
}

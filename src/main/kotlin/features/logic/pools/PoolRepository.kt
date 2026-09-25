package features.logic.pools

import base.exception.model.PoolExceptions
import base.repository.BaseRepository
import base.repository.IndexSpec
import com.mongodb.kotlin.client.coroutine.ClientSession
import features.caches.PoolCache
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class PoolRepository : BaseRepository<Pool>(entityClass = Pool::class), KoinComponent {
    override val cache: PoolCache by inject()

    override val indexes = listOf(IndexSpec.unique("idx_unique_kind_code", "kind", "code"))

    override suspend fun validateBeforeInsert(entity: Pool, session: ClientSession) {
        PoolRules.problem(entity)?.let { throw PoolExceptions.funException("validateBeforeInsert", it) }
    }

    /** Правка администратора меняет только состав: тег и вид - это личность пула, от них его `_id`. */
    override suspend fun validateBeforeUpdate(changes: Map<String, Any?>) {
        if ("code" in changes || "kind" in changes) throw PoolExceptions.funException("validateBeforeUpdate", "tag and kind are fixed")
        val entries = changes["entries"] ?: return
        val broken = entries !is Map<*, *> || entries.any { (code, weight) -> code.toString().isBlank() || (weight as? Number)?.toLong()?.let { it < 0 } != false }
        if (broken) throw PoolExceptions.funException("validateBeforeUpdate", "entries: $entries")
    }
}

/** Правила документа пула - общие для сида и правки администратора. */
object PoolRules {
    /** Ошибка пула, названная по его тегу, или null. */
    fun problem(pool: Pool): String? = when {
        pool.code.isBlank() -> "${pool.kind}: blank tag"
        pool.entries.keys.any { it.isBlank() } -> "${pool.kind} ${pool.code}: blank entry"
        pool.entries.values.any { it < 0 } -> "${pool.kind} ${pool.code}: negative weight"
        else -> null
    }
}

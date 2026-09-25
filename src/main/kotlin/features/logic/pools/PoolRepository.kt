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

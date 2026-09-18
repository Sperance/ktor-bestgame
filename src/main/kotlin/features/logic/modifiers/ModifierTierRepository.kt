package features.logic.modifiers

import base.exception.model.ModifierExceptions
import base.repository.BaseRepository
import base.repository.UniqueIndexConfig
import com.mongodb.client.model.Filters
import com.mongodb.kotlin.client.coroutine.ClientSession
import features.caches.ModifierDefinitionCache
import features.caches.ModifierTierCache
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ModifierTierRepository : BaseRepository<ModifierTier>(
    entityClass = ModifierTier::class
), KoinComponent {
    private val cache: ModifierTierCache by inject()
    private val definitionCache: ModifierDefinitionCache by inject()
    private val definitionRepository: ModifierDefinitionRepository by inject()

    init {
        initialize(
            uniqueIndexes = listOf(
                UniqueIndexConfig(
                    indexName = "idx_unique_modifier_tier",
                    fields = listOf("modifierId", "tier")
                )
            ),
            indexedFields = listOf("modifierId")
        )
    }

    override suspend fun validateBeforeInsert(entity: ModifierTier, session: ClientSession) {
        if (entity.tier <= 0)
            throw ModifierExceptions.funExceptionTier("validateBeforeInsert", entity.tier.toString())
        if (entity.valueMin > entity.valueMax)
            throw ModifierExceptions.funExceptionTierRange("validateBeforeInsert", "${entity.valueMin}..${entity.valueMax}")

        val exists = definitionCache.findById(entity.modifierId) != null
                || definitionRepository.findById(entity.modifierId, session) != null
        if (!exists) throw ModifierExceptions.funExceptionNotFound("validateBeforeInsert", entity.modifierId)
    }

    override suspend fun validateAfterInsert(entity: ModifierTier, session: ClientSession) {
        cache.addItem(entity)
    }

    override suspend fun validateAfterDelete(entity: ModifierTier, session: ClientSession, softDelete: Boolean) {
        cache.removeItem(entity)
    }

    override suspend fun validateAfterUpdate(entity: ModifierTier, session: ClientSession) {
        cache.updateItem(entity)
    }

    /**
     * Все тиры указанного модификатора, отсортированные по возрастанию тира.
     */
    suspend fun findByModifier(modifierId: String): List<ModifierTier> =
        findByFilter(Filters.eq("modifierId", modifierId)).sortedBy { it.tier }
}

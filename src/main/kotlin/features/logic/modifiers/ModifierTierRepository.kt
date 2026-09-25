package features.logic.modifiers

import base.exception.model.ModifierExceptions
import base.repository.BaseRepository
import base.repository.IndexSpec
import com.mongodb.client.model.Filters
import com.mongodb.kotlin.client.coroutine.ClientSession
import features.caches.ModifierDefinitionCache
import features.caches.ModifierTierCache
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ModifierTierRepository : BaseRepository<ModifierTier>(
    entityClass = ModifierTier::class
), KoinComponent {
    override val cache: ModifierTierCache by inject()
    private val definitionCache: ModifierDefinitionCache by inject()
    private val definitionRepository: ModifierDefinitionRepository by inject()

    // Уникальный (modifierId, tier) покрывает и выборку по modifierId
    override val indexes = listOf(IndexSpec.unique("idx_unique_modifier_tier", "modifierId", "tier"))

    override suspend fun validateBeforeInsert(entity: ModifierTier, session: ClientSession) {
        if (entity.tier <= 0)
            throw ModifierExceptions.funExceptionTier("validateBeforeInsert", entity.tier.toString())
        if (entity.values.isEmpty())
            throw ModifierExceptions.funExceptionTierRange("validateBeforeInsert", "no values")
        entity.values.firstOrNull { it.valueMin > it.valueMax }?.let {
            throw ModifierExceptions.funExceptionTierRange("validateBeforeInsert", "${it.valueMin}..${it.valueMax}")
        }

        val definition = definitionCache.findById(entity.modifierId)
            ?: definitionRepository.findById(entity.modifierId, session)
            ?: throw ModifierExceptions.funExceptionNotFound("validateBeforeInsert", entity.modifierId)

        // У составного модификатора на каждый эффект должен быть свой диапазон
        if (definition.effects.size != entity.values.size)
            throw ModifierExceptions.funExceptionTierEffects(
                "validateBeforeInsert",
                "${definition.code}: ${definition.effects.size} effects, ${entity.values.size} values"
            )
    }

    /**
     * Все тиры указанного модификатора, отсортированные по возрастанию тира.
     */
    suspend fun findByModifier(modifierId: String): List<ModifierTier> =
        findByFilter(Filters.eq("modifierId", modifierId)).sortedBy { it.tier }
}

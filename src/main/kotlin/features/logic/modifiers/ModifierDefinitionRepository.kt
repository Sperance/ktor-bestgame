package features.logic.modifiers

import base.exception.model.ModifierExceptions
import base.repository.BaseRepository
import base.repository.IndexSpec
import com.mongodb.kotlin.client.coroutine.ClientSession
import features.caches.ModifierDefinitionCache
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ModifierDefinitionRepository : BaseRepository<ModifierDefinition>(
    entityClass = ModifierDefinition::class
), KoinComponent {
    override val cache: ModifierDefinitionCache by inject()

    override val indexes = listOf(IndexSpec.unique("idx_unique_code", "code"))

    override suspend fun validateBeforeInsert(entity: ModifierDefinition, session: ClientSession) {
        if (entity.code.isBlank()) throw ModifierExceptions.funExceptionCode("validateBeforeInsert", entity.code)
        if (entity.effects.isEmpty()) throw ModifierExceptions.funExceptionNoEffects("validateBeforeInsert", entity.code)
        // Тиры внутри описания (с 0.56.0): у каждого уровень и по диапазону на каждый эффект
        entity.tiers.forEach { tier ->
            if (tier.level < 1) throw ModifierExceptions.funExceptionTier("validateBeforeInsert", "${entity.code}: ${tier.level}")
            if (tier.values.size != entity.effects.size)
                throw ModifierExceptions.funExceptionTierEffects("validateBeforeInsert", "${entity.code}: ${entity.effects.size} effects, ${tier.values.size} values")
            tier.values.firstOrNull { it.size != 2 || it[0] > it[1] }?.let {
                throw ModifierExceptions.funExceptionTierRange("validateBeforeInsert", "${entity.code}: $it")
            }
        }

        entity.effects.forEach { effect ->
            val source = effect.perStat ?: return@forEach

            // Единственное, что делает циклы конверсий невыразимыми
            if (source.order >= effect.stat.order)
                throw ModifierExceptions.funExceptionConversionOrder(
                    "validateBeforeInsert",
                    "${entity.code}: $source(${source.order}) -> ${effect.stat}(${effect.stat.order})"
                )
            if (effect.perAmount <= 0.0)
                throw ModifierExceptions.funExceptionConversionAmount("validateBeforeInsert", "${entity.code}: ${effect.perAmount}")
            if (entity.isLocal)
                throw ModifierExceptions.funExceptionLocalConversion("validateBeforeInsert", entity.code)
        }
    }

}

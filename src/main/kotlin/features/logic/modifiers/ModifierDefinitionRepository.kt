package features.logic.modifiers

import base.exception.model.ModifierExceptions
import base.repository.BaseRepository
import base.repository.UniqueIndexConfig
import com.mongodb.kotlin.client.coroutine.ClientSession
import features.caches.ModifierDefinitionCache
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ModifierDefinitionRepository : BaseRepository<ModifierDefinition>(
    entityClass = ModifierDefinition::class
), KoinComponent {
    override val cache: ModifierDefinitionCache by inject()

    init {
        initialize(uniqueIndexes = listOf(
            UniqueIndexConfig(
                indexName = "idx_unique_code",
                fields = listOf("code")
            )
        ))
    }

    override suspend fun validateBeforeInsert(entity: ModifierDefinition, session: ClientSession) {
        if (entity.code.isBlank()) throw ModifierExceptions.funExceptionCode("validateBeforeInsert", entity.code)
        if (entity.effects.isEmpty()) throw ModifierExceptions.funExceptionNoEffects("validateBeforeInsert", entity.code)

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

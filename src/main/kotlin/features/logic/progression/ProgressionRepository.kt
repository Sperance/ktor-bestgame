package features.logic.progression

import base.exception.model.ProgressionExceptions
import base.repository.BaseRepository
import base.repository.UniqueIndexConfig
import com.mongodb.kotlin.client.coroutine.ClientSession
import features.caches.CharacterClassCache
import features.caches.ExperienceLevelCache
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class CharacterClassRepository : BaseRepository<CharacterClass>(
    entityClass = CharacterClass::class
), KoinComponent {
    private val cache: CharacterClassCache by inject()

    init {
        initialize(uniqueIndexes = listOf(
            UniqueIndexConfig(indexName = "idx_unique_code", fields = listOf("code"))
        ))
    }

    override suspend fun validateBeforeInsert(entity: CharacterClass, session: ClientSession) {
        if (entity.code.isBlank()) throw ProgressionExceptions.funExceptionClassCode("validateBeforeInsert", entity.code)
    }

    override suspend fun validateAfterInsert(entity: CharacterClass, session: ClientSession) {
        cache.addItem(entity)
    }

    override suspend fun validateAfterDelete(entity: CharacterClass, session: ClientSession, softDelete: Boolean) {
        cache.removeItem(entity)
    }

    override suspend fun validateAfterUpdate(entity: CharacterClass, session: ClientSession) {
        cache.updateItem(entity)
    }
}

class ExperienceLevelRepository : BaseRepository<ExperienceLevel>(
    entityClass = ExperienceLevel::class
), KoinComponent {
    private val cache: ExperienceLevelCache by inject()

    init {
        initialize(uniqueIndexes = listOf(
            UniqueIndexConfig(indexName = "idx_unique_level", fields = listOf("level"))
        ))
    }

    override suspend fun validateBeforeInsert(entity: ExperienceLevel, session: ClientSession) {
        if (entity.level <= 0) throw ProgressionExceptions.funExceptionLevel("validateBeforeInsert", entity.level.toString())
        if (entity.experience < 0) throw ProgressionExceptions.funExceptionExperience("validateBeforeInsert", entity.experience.toString())
    }

    override suspend fun validateAfterInsert(entity: ExperienceLevel, session: ClientSession) {
        cache.addItem(entity)
    }

    override suspend fun validateAfterDelete(entity: ExperienceLevel, session: ClientSession, softDelete: Boolean) {
        cache.removeItem(entity)
    }

    override suspend fun validateAfterUpdate(entity: ExperienceLevel, session: ClientSession) {
        cache.updateItem(entity)
    }
}

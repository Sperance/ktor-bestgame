package features.logic.skilltree

import base.exception.model.SkillTreeExceptions
import base.repository.BaseRepository
import base.repository.UniqueIndexConfig
import com.mongodb.kotlin.client.coroutine.ClientSession
import features.caches.SkillTreeCache
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SkillTreeNodeRepository : BaseRepository<SkillTreeNode>(
    entityClass = SkillTreeNode::class
), KoinComponent {
    private val cache: SkillTreeCache by inject()

    init {
        initialize(uniqueIndexes = listOf(
            UniqueIndexConfig(
                indexName = "idx_unique_code",
                fields = listOf("code")
            )
        ))
    }

    override suspend fun validateBeforeInsert(entity: SkillTreeNode, session: ClientSession) {
        if (entity.code.isBlank()) throw SkillTreeExceptions.funExceptionCode("validateBeforeInsert", entity.code)
        if (entity.cost < 0) throw SkillTreeExceptions.funExceptionCost("validateBeforeInsert", entity.cost.toString())
    }

    override suspend fun validateAfterInsert(entity: SkillTreeNode, session: ClientSession) {
        cache.addItem(entity)
    }

    override suspend fun validateAfterDelete(entity: SkillTreeNode, session: ClientSession, softDelete: Boolean) {
        cache.removeItem(entity)
    }

    override suspend fun validateAfterUpdate(entity: SkillTreeNode, session: ClientSession) {
        cache.updateItem(entity)
    }
}

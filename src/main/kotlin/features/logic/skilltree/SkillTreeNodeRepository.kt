package features.logic.skilltree

import base.exception.model.SkillTreeExceptions
import base.repository.BaseRepository
import base.repository.IndexSpec
import com.mongodb.kotlin.client.coroutine.ClientSession
import features.caches.SkillTreeCache
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SkillTreeNodeRepository : BaseRepository<SkillTreeNode>(
    entityClass = SkillTreeNode::class
), KoinComponent {
    override val cache: SkillTreeCache by inject()

    override val indexes = listOf(IndexSpec.unique("idx_unique_code", "code"))

    override suspend fun validateBeforeInsert(entity: SkillTreeNode, session: ClientSession) {
        if (entity.code.isBlank()) throw SkillTreeExceptions.funExceptionCode("validateBeforeInsert", entity.code)
        if (entity.cost < 0) throw SkillTreeExceptions.funExceptionCost("validateBeforeInsert", entity.cost.toString())
    }

}

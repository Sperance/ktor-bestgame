package features.caches

import features.logic.skilltree.SkillTreeGraph
import features.logic.skilltree.SkillTreeNode
import features.logic.skilltree.SkillTreeNodeRepository

/**
 * Кэш дерева навыков. Граф строится один раз на снимок, обход делегируется [SkillTreeGraph].
 */
class SkillTreeCache(
    repository: SkillTreeNodeRepository
) : MongoCache<SkillTreeNode, SkillTreeNodeRepository>(repository) {

    private val built = derived { items -> SkillTreeGraph(items) }

    fun graph(): SkillTreeGraph = built.get()

    fun findByCode(code: String): SkillTreeNode? = graph().node(code)
}

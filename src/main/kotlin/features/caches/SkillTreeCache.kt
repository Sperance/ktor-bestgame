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

    /**
     * Стартовые узлы дерева - точки, с которых персонаж может начать.
     */
    fun startNodes(): List<SkillTreeNode> = graph().startNodes

    fun neighbours(code: String): Set<String> = graph().neighbours(code)

    fun isAdjacentTo(code: String, taken: Collection<String>): Boolean = graph().isAdjacentTo(code, taken)

    fun isConnected(taken: Collection<String>): Boolean = graph().isConnected(taken)
}

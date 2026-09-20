package features.caches

import application.enums.EnumSkillNodeType
import features.logic.skilltree.SkillTreeGraph
import features.logic.skilltree.SkillTreeNode
import features.logic.skilltree.SkillTreeNodeRepository

/**
 * Кэш дерева навыков. Обход графа делегируется [SkillTreeGraph].
 */
class SkillTreeCache(
    repository: SkillTreeNodeRepository
) : MongoCache<SkillTreeNode, SkillTreeNodeRepository>(repository) {

    fun findByCode(code: String): SkillTreeNode? = getCache().find { it.code == code }

    fun findAllByCode(codes: Collection<String>): List<SkillTreeNode> = codes.mapNotNull { findByCode(it) }

    /**
     * Стартовые узлы дерева - точки, с которых персонаж может начать.
     */
    fun startNodes(): List<SkillTreeNode> = getCache().filter { it.type == EnumSkillNodeType.START }

    fun neighbours(code: String): Set<String> = SkillTreeGraph.neighbours(getCache(), code)

    fun isAdjacentTo(code: String, taken: Collection<String>): Boolean =
        SkillTreeGraph.isAdjacentTo(getCache(), code, taken)

    fun isConnected(taken: Collection<String>): Boolean = SkillTreeGraph.isConnected(getCache(), taken)
}

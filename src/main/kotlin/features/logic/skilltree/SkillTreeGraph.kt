package features.logic.skilltree

import application.enums.EnumSkillNodeType

/**
 * Правила обхода дерева навыков.
 *
 * Вынесены из кэша отдельно и работают на любом наборе узлов: именно здесь
 * решается, можно ли взять узел и не развалится ли дерево при откате.
 */
object SkillTreeGraph {

    /**
     * Коды узлов, смежных с указанным.
     *
     * Связь двусторонняя, поэтому учитываются и те узлы,
     * которые объявили связь со своей стороны.
     */
    fun neighbours(nodes: Collection<SkillTreeNode>, code: String): Set<String> {
        val declared = nodes.find { it.code == code }?.connections.orEmpty()
        val incoming = nodes.filter { code in it.connections }.map { it.code }
        return (declared + incoming).toSet()
    }

    /**
     * Смежен ли узел хотя бы с одним из взятых.
     */
    fun isAdjacentTo(nodes: Collection<SkillTreeNode>, code: String, taken: Collection<String>): Boolean =
        neighbours(nodes, code).any { it in taken }

    /**
     * Все ли взятые узлы достижимы от стартового по взятым же узлам.
     *
     * Именно эта проверка не даёт откатить узел так, чтобы часть дерева
     * повисла в воздухе.
     */
    fun isConnected(nodes: Collection<SkillTreeNode>, taken: Collection<String>): Boolean {
        if (taken.isEmpty()) return true

        val remaining = taken.toMutableSet()
        val roots = remaining.filter { code ->
            nodes.find { it.code == code }?.type == EnumSkillNodeType.START
        }
        if (roots.isEmpty()) return false

        val queue = ArrayDeque(roots)
        remaining.removeAll(roots.toSet())

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            neighbours(nodes, current).filter { it in remaining }.forEach { next ->
                remaining.remove(next)
                queue.addLast(next)
            }
        }

        return remaining.isEmpty()
    }
}

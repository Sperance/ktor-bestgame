package features.logic.skilltree

import application.enums.EnumSkillNodeType

/**
 * Дерево навыков как граф (с 0.49.0 - построенный один раз на снимок кеша).
 *
 * Связи двусторонние: узел, объявивший связь со своей стороны, попадает в соседи и
 * тому, кто её не объявлял. Соседи и узел по коду - O(1); именно здесь решается,
 * можно ли взять узел и не развалится ли дерево при откате.
 */
class SkillTreeGraph(nodes: Collection<SkillTreeNode>) {
    val byCode: Map<String, SkillTreeNode> = nodes.associateBy { it.code }
    val startNodes: List<SkillTreeNode> = nodes.filter { it.type == EnumSkillNodeType.START }

    private val adjacency: Map<String, Set<String>> = HashMap<String, MutableSet<String>>().also { edges ->
        nodes.forEach { node ->
            val own = edges.getOrPut(node.code) { LinkedHashSet() }
            node.connections.forEach { other ->
                own += other
                edges.getOrPut(other) { LinkedHashSet() } += node.code
            }
        }
    }

    fun node(code: String): SkillTreeNode? = byCode[code]

    /**
     * Коды узлов, смежных с указанным.
     */
    fun neighbours(code: String): Set<String> = adjacency[code].orEmpty()

    /**
     * Смежен ли узел хотя бы с одним из взятых.
     */
    fun isAdjacentTo(code: String, taken: Collection<String>): Boolean {
        val takenSet = taken as? Set<String> ?: taken.toHashSet()
        return neighbours(code).any { it in takenSet }
    }

    /**
     * Все ли взятые узлы достижимы от стартового по взятым же узлам.
     *
     * Именно эта проверка не даёт откатить узел так, чтобы часть дерева
     * повисла в воздухе.
     */
    fun isConnected(taken: Collection<String>): Boolean {
        if (taken.isEmpty()) return true
        val remaining = taken.toHashSet()
        val roots = remaining.filter { byCode[it]?.type == EnumSkillNodeType.START }
        if (roots.isEmpty()) return false
        val queue = ArrayDeque(roots)
        remaining.removeAll(roots.toSet())
        while (queue.isNotEmpty()) {
            neighbours(queue.removeFirst()).forEach { next -> if (remaining.remove(next)) queue.addLast(next) }
        }
        return remaining.isEmpty()
    }
}

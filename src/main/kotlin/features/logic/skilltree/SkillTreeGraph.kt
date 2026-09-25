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
     *
     * Взятое мастерство (с 0.52.0) соседей не открывает: оно лист кластера, и иначе через него
     * можно было бы перепрыгнуть с одного notable на другой, минуя кольцо.
     */
    fun isAdjacentTo(code: String, taken: Collection<String>): Boolean {
        val takenSet = taken as? Set<String> ?: taken.toHashSet()
        return neighbours(code).any { it in takenSet && !isMastery(it) }
    }

    private fun isMastery(code: String) = byCode[code]?.type == EnumSkillNodeType.MASTERY

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
            val current = queue.removeFirst()
            // Мастерство достижимо, но дальше не ведёт - как и при взятии
            if (isMastery(current)) continue
            neighbours(current).forEach { next -> if (remaining.remove(next)) queue.addLast(next) }
        }
        return remaining.isEmpty()
    }
}

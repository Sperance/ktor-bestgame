package com.sperance.exileforge.rules.content

import com.sperance.exileforge.rules.RuleViolation
import com.sperance.exileforge.rules.fail
import kotlinx.serialization.Serializable

@Serializable
enum class SkillNodeType { START, SMALL, NOTABLE, KEYSTONE, JEWEL_SOCKET, MASTERY, ATTRIBUTE }

/**
 * Узел дерева навыков (`tree.json`): граф по [connections] (связь двусторонняя), бонусы [lines] -
 * закреплённые строки без тира; у мастерства и атрибутного узла - [options] на выбор.
 */
@Serializable
data class TreeNode(
    val code: String,
    val type: SkillNodeType,
    val lines: List<Line> = emptyList(),
    val connections: List<String> = emptyList(),
    val options: List<List<Line>> = emptyList(),
    val cost: Int = 1,
    val x: Int = 0,
    val y: Int = 0,
)

@Serializable
data class TreeFile(val nodes: List<TreeNode> = emptyList())

/** Взятый узел дерева у героя: код и, у мастерства или атрибутного узла, выбранный вариант. */
@Serializable
data class TakenNode(val code: String, val choice: Int? = null)

class TreeGraph(nodes: Collection<TreeNode>) {
    val byCode: Map<String, TreeNode> = nodes.associateBy { it.code }

    private val adjacency: Map<String, Set<String>> = HashMap<String, MutableSet<String>>().also { edges ->
        nodes.forEach { node ->
            val own = edges.getOrPut(node.code) { LinkedHashSet() }
            node.connections.forEach { other -> own += other; edges.getOrPut(other) { LinkedHashSet() } += node.code }
        }
    }

    fun node(code: String): TreeNode? = byCode[code]
    fun neighbours(code: String): Set<String> = adjacency[code].orEmpty()

    /** Строки взятых узлов: у узла с выбором - строки выбранного варианта. */
    fun lines(taken: Collection<TakenNode>): List<Line> = taken.flatMap { t ->
        val node = byCode[t.code] ?: return@flatMap emptyList()
        t.choice?.let { node.options.getOrNull(it) } ?: node.lines
    }

    /** Очки, потраченные на взятое: стартовый узел бесплатен. */
    fun spent(taken: Collection<TakenNode>): Int = taken.sumOf { t -> byCode[t.code]?.takeIf { it.type != SkillNodeType.START }?.cost ?: 0 }

    private fun isMastery(code: String) = byCode[code]?.type == SkillNodeType.MASTERY

    /** Смежен ли узел с взятым; взятое мастерство соседей не открывает. */
    fun isAdjacentTo(code: String, taken: Collection<String>): Boolean {
        val set = taken as? Set<String> ?: taken.toHashSet()
        return neighbours(code).any { it in set && !isMastery(it) }
    }

    fun isConnected(taken: Collection<String>): Boolean {
        if (taken.isEmpty()) return true
        val remaining = taken.toHashSet()
        val roots = remaining.filter { byCode[it]?.type == SkillNodeType.START }
        if (roots.isEmpty()) return false
        val queue = ArrayDeque(roots)
        remaining.removeAll(roots.toSet())
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (isMastery(current)) continue
            neighbours(current).forEach { next -> if (remaining.remove(next)) queue.addLast(next) }
        }
        return remaining.isEmpty()
    }

    fun validate(modifier: (String) -> ModifierDef?) {
        byCode.values.forEach { node ->
            node.connections.forEach { if (it !in byCode) fail("tree: ${node.code} connects to unknown $it") }
            (node.lines + node.options.flatten()).forEach { line ->
                val def = modifier(line.code) ?: fail("tree: modifier ${line.code} of ${node.code}")
                if (line.values.size != def.effects.size) fail("tree: values of ${line.code} on ${node.code}")
            }
            if ((node.type == SkillNodeType.MASTERY || node.type == SkillNodeType.ATTRIBUTE) != node.options.isNotEmpty()) fail("tree: options of ${node.code}")
            if (node.cost < 0) fail("tree: cost of ${node.code}")
        }
    }
}

/** Правила прокачки дерева: со стартового узла класса, только соседи взятого, откат без обрыва. */
object TreeAllocation {
    fun requireAllocatable(graph: TreeGraph, node: TreeNode, taken: Collection<String>, startNode: String, available: Int, choice: Int?) {
        if (node.options.isEmpty() != (choice == null) || (choice != null && choice !in node.options.indices))
            throw RuleViolation("ST_018", listOf("${node.code}: $choice of ${node.options.size}"))
        if (node.code in taken) throw RuleViolation("ST_005", listOf(node.code))
        if (node.type == SkillNodeType.START) {
            if (taken.isNotEmpty()) throw RuleViolation("ST_009", listOf(taken.first()))
            if (node.code != startNode) throw RuleViolation("ST_013", listOf("${node.code}, class starts at $startNode"))
        } else {
            if (taken.isEmpty()) throw RuleViolation("ST_010", listOf(node.code))
            if (!graph.isAdjacentTo(node.code, taken)) throw RuleViolation("ST_007", listOf(node.code))
        }
        if (node.cost > available) throw RuleViolation("ST_008", listOf("need ${node.cost}, available $available"))
    }

    fun requireRefundable(graph: TreeGraph, node: TreeNode, taken: Collection<String>) {
        if (node.code !in taken) throw RuleViolation("ST_006", listOf(node.code))
        if (node.type == SkillNodeType.START) throw RuleViolation("ST_012", listOf(node.code))
        if (!graph.isConnected(taken.filterNot { it == node.code })) throw RuleViolation("ST_011", listOf(node.code))
    }

    fun requireSocketEmpty(node: TreeNode, socketed: Collection<String>) {
        if (node.type == SkillNodeType.JEWEL_SOCKET && node.code in socketed) throw RuleViolation("ST_014", listOf(node.code))
    }
}

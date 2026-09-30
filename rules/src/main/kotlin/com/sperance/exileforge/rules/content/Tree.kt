package com.sperance.exileforge.rules.content

import com.sperance.exileforge.rules.RuleViolation
import com.sperance.exileforge.rules.fail
import com.sperance.exileforge.rules.text.LocaleKey
import kotlinx.serialization.Serializable

@Serializable
enum class SkillNodeType { START, SMALL, NOTABLE, KEYSTONE, JEWEL_SOCKET, MASTERY, ATTRIBUTE }

/**
 * Узел дерева навыков (`tree.json`): граф по [connections] (связь двусторонняя), бонусы [lines] -
 * закреплённые строки без тира; у мастерства и атрибутного узла - [options] на выбор. [only] (1.17.0) -
 * стартовый узел класса, которому узел достаётся один: ветки Сиона чужим классам закрыты.
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
    val only: String? = null,
) {
    fun openTo(startNode: String): Boolean = only == null || only == startNode
}

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

    fun isConnected(taken: Collection<String>): Boolean = taken.isEmpty() || detached(taken).isEmpty()

    /** Взятое, что не держится за старт (1.52.0): без стартового узла - всё. */
    fun detached(taken: Collection<String>): Set<String> {
        val remaining = taken.toHashSet()
        val roots = remaining.filter { byCode[it]?.type == SkillNodeType.START }
        if (roots.isEmpty()) return remaining
        val queue = ArrayDeque(roots)
        remaining.removeAll(roots.toSet())
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (isMastery(current)) continue
            neighbours(current).forEach { next -> if (remaining.remove(next)) queue.addLast(next) }
        }
        return remaining
    }

    fun validate(modifier: (String) -> ModifierDef?) {
        byCode.values.forEach { node ->
            node.connections.forEach { if (it !in byCode) fail("tree: ${node.code} connects to unknown $it") }
            node.only?.let { if (byCode[it]?.type != SkillNodeType.START) fail("tree: ${node.code} is only for $it, not a start") }
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
    /**
     * Кратчайший путь к [target] от взятого (1.37.0): невзятые узлы по порядку, последний - сама цель; null - пути нет.
     * Узел с выбором (атрибут, мастерство) бывает только целью: вариант промежуточного узла герой выбирает сам, шагом.
     * Путь берётся весь разом - за сумму цен его узлов.
     */
    fun path(graph: TreeGraph, taken: Collection<String>, startNode: String, target: String): List<String>? {
        val goal = graph.node(target) ?: return null
        val set = taken.toHashSet()
        if (target in set || goal.type == SkillNodeType.START || !goal.openTo(startNode)) return null
        val from = HashMap<String, String>()
        val queue = ArrayDeque(set.filter { graph.node(it)?.type != SkillNodeType.MASTERY })
        val seen = HashSet(set)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (next in graph.neighbours(current)) {
                if (!seen.add(next)) continue
                val node = graph.node(next) ?: continue
                if (node.type == SkillNodeType.START || !node.openTo(startNode)) continue
                from[next] = current
                if (next == target) return generateSequence(target) { from[it]?.takeIf { prev -> prev !in set } }.toList().reversed()
                if (node.options.isEmpty() && node.type != SkillNodeType.MASTERY) queue.addLast(next)
            }
        }
        return null
    }

    fun requireAllocatable(graph: TreeGraph, node: TreeNode, taken: Collection<String>, startNode: String, available: Int, choice: Int?) {
        if (node.options.isEmpty() != (choice == null) || (choice != null && choice !in node.options.indices))
            throw RuleViolation("ST_018", listOf("${node.code}: $choice of ${node.options.size}"))
        if (node.code in taken) throw RuleViolation("ST_005", listOf(LocaleKey.skillNodeName(node.code)))
        if (node.type == SkillNodeType.START) {
            if (taken.isNotEmpty()) throw RuleViolation("ST_009", listOf(LocaleKey.skillNodeName(taken.first())))
            if (node.code != startNode) throw RuleViolation("ST_013", listOf(LocaleKey.skillNodeName(startNode)))
        } else {
            if (taken.isEmpty()) throw RuleViolation("ST_010", listOf(node.code))
            if (!graph.isAdjacentTo(node.code, taken)) throw RuleViolation("ST_007", listOf(LocaleKey.skillNodeName(node.code)))
            if (!node.openTo(startNode)) throw RuleViolation("ST_021", listOf(LocaleKey.skillNodeName(node.code)))
        }
        if (node.cost > available) throw RuleViolation("ST_008", listOf(node.cost.toString(), available.toString()))
    }

    fun requireRefundable(graph: TreeGraph, node: TreeNode, taken: Collection<String>) {
        if (node.code !in taken) throw RuleViolation("ST_006", listOf(LocaleKey.skillNodeName(node.code)))
        if (node.type == SkillNodeType.START) throw RuleViolation("ST_012", listOf(LocaleKey.skillNodeName(node.code)))
        if (!graph.isConnected(taken.filterNot { it == node.code })) throw RuleViolation("ST_011", listOf(LocaleKey.skillNodeName(node.code)))
    }

    /** Ветка узла (1.52.0): он сам и всё взятое, что без него отрывается от старта, - снимается одним откатом. */
    fun branch(graph: TreeGraph, node: TreeNode, taken: Collection<String>): List<String> {
        if (node.code !in taken) throw RuleViolation("ST_006", listOf(LocaleKey.skillNodeName(node.code)))
        if (node.type == SkillNodeType.START) throw RuleViolation("ST_012", listOf(LocaleKey.skillNodeName(node.code)))
        return listOf(node.code) + graph.detached(taken.filterNot { it == node.code })
    }

    fun requireSocketEmpty(node: TreeNode, socketed: Collection<String>) {
        if (node.type == SkillNodeType.JEWEL_SOCKET && node.code in socketed) throw RuleViolation("ST_014", listOf(node.code))
    }

    /**
     * Уникальный самоцвет - один такой на героя (1.31.0): [others] - шаблоны самоцветов, уже стоящих в других гнёздах.
     * Отказ `ST_022` с ключом имени самоцвета.
     */
    fun requireUniqueJewelFree(template: ItemTemplate, others: Collection<String>) {
        if (template.slot == Slot.JEWEL && template.unique && template.code in others) throw RuleViolation("ST_022", listOf(LocaleKey.equipmentName(template.code)))
    }
}

/**
 * План дерева (1.45.0): узлы, которые герой собирается взять, по порядку. Сервер хранит его и берёт узлы сам,
 * как только на них хватает очков: [follow] идёт по плану и берёт подряд всё, что законно взять сейчас, - первый
 * узел, на который не хватает очков или к которому ещё нет пути, останавливает шаг: порядок плана - порядок игрока.
 */
object TreePlan {
    /** Узлы плана [plan], которые можно взять сейчас по порядку, при [available] свободных очках. */
    fun follow(graph: TreeGraph, plan: List<TakenNode>, taken: Collection<String>, startNode: String, available: Int): List<TakenNode> {
        val have = taken.toMutableList()
        var left = available
        val took = mutableListOf<TakenNode>()
        for (step in plan) {
            if (step.code in have) continue
            val node = graph.node(step.code) ?: break
            val legal = runCatching { TreeAllocation.requireAllocatable(graph, node, have, startNode, left, step.choice) }.isSuccess
            if (!legal) break
            have += step.code
            left -= node.cost
            took += step
        }
        return took
    }

    /** Разбор плана с провода: `код` или `код:вариант` через запятую. */
    fun parse(text: String): List<TakenNode> = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { part ->
        val code = part.substringBefore(':')
        TakenNode(code, part.substringAfter(':', "").takeIf { it.isNotEmpty() }?.toIntOrNull())
    }
}

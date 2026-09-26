package com.sperance.exileforge.rules.content

import com.sperance.exileforge.rules.RuleViolation
import com.sperance.exileforge.rules.fail
import kotlinx.serialization.Serializable

@Serializable
enum class AtlasNodeKind { START, SMALL, NOTABLE, KEYSTONE }

/** Узел атласа: [lines] - закреплённые строки описаний источника ATLAS, [parents] - откуда ведёт путь. */
@Serializable
data class AtlasNode(
    val code: String,
    val x: Double,
    val y: Double,
    val kind: AtlasNodeKind,
    val parents: List<String> = emptyList(),
    val lines: List<Line> = emptyList(),
)

@Serializable
data class AtlasRespec(val perNode: Long, val perLevel: Long = 0) {
    fun price(level: Int, nodes: Int): Long = (perNode + perLevel * level) * nodes
}

/** Файл `atlas.json`: очки за достижения, потолок, цена отката и узлы. */
@Serializable
data class AtlasTree(val points: Map<String, Int>, val respec: AtlasRespec, val nodes: List<AtlasNode>, val cap: Int = Int.MAX_VALUE)

class AtlasGraph(nodes: Collection<AtlasNode>) {
    val byCode: Map<String, AtlasNode> = nodes.associateBy { it.code }
    val start: String = nodes.first { it.kind == AtlasNodeKind.START }.code

    private val adjacency: Map<String, Set<String>> = HashMap<String, MutableSet<String>>().also { edges ->
        nodes.forEach { node ->
            edges.getOrPut(node.code) { LinkedHashSet() }
            node.parents.forEach { parent ->
                edges.getValue(node.code) += parent
                edges.getOrPut(parent) { LinkedHashSet() } += node.code
            }
        }
    }

    fun node(code: String): AtlasNode? = byCode[code]
    fun neighbours(code: String): Set<String> = adjacency[code].orEmpty()
    fun isAdjacentTo(code: String, allocated: Collection<String>): Boolean = neighbours(code).any { it == start || it in allocated }

    fun isConnected(allocated: Collection<String>): Boolean {
        val remaining = allocated.toHashSet().apply { remove(start) }
        val queue = ArrayDeque(listOf(start))
        while (queue.isNotEmpty() && remaining.isNotEmpty()) {
            neighbours(queue.removeFirst()).forEach { next -> if (remaining.remove(next)) queue.addLast(next) }
        }
        return remaining.isEmpty()
    }

    fun validate(tree: AtlasTree, stats: StatRegistry, modifier: (String) -> ModifierDef?) {
        tree.points.forEach { (kind, amount) -> if (kind !in AtlasPoints.KINDS || amount < 0) fail("atlas: points $kind") }
        if (tree.cap <= 0) fail("atlas: cap")
        if (tree.respec.perNode < 0 || tree.respec.perLevel < 0) fail("atlas: respec")
        if (byCode.size != tree.nodes.size) fail("atlas: node codes")
        val starts = tree.nodes.filter { it.kind == AtlasNodeKind.START }
        if (starts.size != 1 || starts.single().parents.isNotEmpty()) fail("atlas: exactly one START without parents")
        tree.nodes.forEach { node ->
            if (node.code.isBlank()) fail("atlas: blank node code")
            if (node.kind != AtlasNodeKind.START && node.parents.isEmpty()) fail("atlas: node ${node.code} without parents")
            node.parents.forEach { if (it !in byCode || it == node.code) fail("atlas: parent $it of ${node.code}") }
            node.lines.forEach { line ->
                val def = modifier(line.code) ?: fail("atlas: modifier ${line.code} of ${node.code}")
                if (def.source != Source.ATLAS || line.values.size != def.effects.size) fail("atlas: line ${line.code} of ${node.code}")
                def.effects.forEach { if (stats[it.stat]?.group != StatGroup.ATLAS) fail("atlas: stat ${it.stat} of ${node.code}") }
            }
        }
        val done = HashSet<String>()
        val path = HashSet<String>()
        fun visit(code: String) {
            if (code in done) return
            if (!path.add(code)) fail("atlas: cycle through $code")
            byCode.getValue(code).parents.forEach(::visit)
            path.remove(code)
            done += code
        }
        tree.nodes.forEach { visit(it.code) }
        if (!isConnected(byCode.keys)) fail("atlas: nodes unreachable from $start")
    }
}

/** Очки атласа: ключи `<вид>:<зона>`, каждый один раз. */
object AtlasPoints {
    const val BOSS = "boss"
    const val RARE = "rare"
    const val VAAL = "vaal"
    val KINDS = setOf(BOSS, RARE, VAAL)

    fun key(kind: String, mapCode: String) = "$kind:$mapCode"
    fun earn(earned: MutableCollection<String>, kind: String, mapCode: String): Boolean = key(kind, mapCode).let { it !in earned && earned.add(it) }
    fun total(rule: Map<String, Int>, earned: Collection<String>, cap: Int = Int.MAX_VALUE): Int = minOf(cap, earned.sumOf { rule[it.substringBefore(':')] ?: 0 })
    fun available(rule: Map<String, Int>, earned: Collection<String>, allocated: Collection<String>, cap: Int = Int.MAX_VALUE): Int = total(rule, earned, cap) - allocated.size
}

/** Проверки взятия и отката узла атласа. */
object AtlasAllocation {
    fun requireAllocatable(graph: AtlasGraph, code: String, allocated: Collection<String>, available: Int) {
        graph.node(code) ?: throw RuleViolation("AT_002", listOf(code))
        if (code == graph.start) throw RuleViolation("AT_008", listOf(code))
        if (code in allocated) throw RuleViolation("AT_003", listOf(code))
        if (!graph.isAdjacentTo(code, allocated)) throw RuleViolation("AT_005", listOf(code))
        if (available < 1) throw RuleViolation("AT_006", listOf(available.toString()))
    }

    fun requireRefundable(graph: AtlasGraph, code: String, allocated: Collection<String>) {
        graph.node(code) ?: throw RuleViolation("AT_002", listOf(code))
        if (code == graph.start) throw RuleViolation("AT_008", listOf(code))
        if (code !in allocated) throw RuleViolation("AT_004", listOf(code))
        if (!graph.isConnected(allocated - code)) throw RuleViolation("AT_007", listOf(code))
    }
}

/** Что атлас героя даёт картам: строки взятых узлов, сложенные по характеристикам. */
class AtlasBonuses(val effects: Map<String, Double> = emptyMap()) {
    operator fun get(stat: String): Double = effects[stat] ?: 0.0

    val quantity get() = this["ATLAS_QUANTITY"]
    val rarity get() = this["ATLAS_RARITY"]
    val experience get() = this["ATLAS_EXPERIENCE"]
    val vaalReward get() = this["ATLAS_VAAL_REWARD"]
    val vaalMinMods get() = this["ATLAS_VAAL_MIN_MODS"].toInt()
    val chests get() = this["ATLAS_CHESTS"].toInt()
    val mapNext get() = this["ATLAS_MAP_NEXT"]
    val mapRare get() = this["ATLAS_MAP_RARE"]
    val mapAffix get() = this["ATLAS_MAP_AFFIX"]
    val bossLoot get() = this["ATLAS_BOSS_LOOT"]
    val chestLoot get() = this["ATLAS_CHEST_LOOT"]
    val gold get() = this["ATLAS_GOLD"]
    val mapEffect get() = relative("ATLAS_MAP_EFFECT")
    val crystalChance get() = this["ATLAS_CRYSTAL_CHANCE"]
    val crystalEssences get() = this["ATLAS_CRYSTAL_ESSENCES"]
    val crystalTier get() = this["ATLAS_CRYSTAL_TIER"]
    val crystals get() = this["ATLAS_CRYSTALS"].toInt()
    val crystalsMore get() = this["ATLAS_CRYSTALS_MORE"]
    val books get() = this["ATLAS_BOOKS"]
    val booksOwn get() = this["ATLAS_BOOKS_OWN"]
    val abyssChance get() = this["ATLAS_ABYSS_CHANCE"]
    val abyssExtra get() = this["ATLAS_ABYSS_EXTRA"]
    val abyssDepth get() = this["ATLAS_ABYSS_DEPTH"].toInt()
    val abyssKeep get() = this["ATLAS_ABYSS_KEEP"].coerceIn(0.0, 100.0)
    val extraRareMods get() = this["ATLAS_RARE_MODS"].toInt()

    fun recipeChance(chance: Double) = chance * relative("ATLAS_RECIPE")
    fun vaalUniqueChance(chance: Double) = chance * relative("ATLAS_VAAL_UNIQUE")
    fun mapChance(chance: Double) = chance * relative("ATLAS_MAP_DROP")
    fun bossUniqueChance(chance: Double) = chance * relative("ATLAS_BOSS_UNIQUE")
    fun bossRespawnHours(hours: Double) = hours * (1 - this["ATLAS_BOSS_RESPAWN"].coerceIn(0.0, RESPAWN_CAP) / 100)

    private fun relative(stat: String) = (1 + this[stat] / 100).coerceAtLeast(0.0)

    companion object {
        const val RESPAWN_CAP = 90.0
        val NONE = AtlasBonuses()

        fun of(graph: AtlasGraph, allocated: Collection<String>, modifier: (String) -> ModifierDef?): AtlasBonuses {
            val effects = mutableMapOf<String, Double>()
            allocated.mapNotNull(graph::node).flatMap { it.lines }.forEach { line ->
                modifier(line.code)?.effects?.forEachIndexed { index, effect -> effects.merge(effect.stat, line.values.getOrElse(index) { 0.0 }, Double::plus) }
            }
            return AtlasBonuses(effects)
        }
    }
}

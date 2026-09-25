package features.logic.atlas

import application.enums.EnumStatStock
import base.exception.model.AtlasExceptions

/**
 * Атлас как граф: связи родитель - ребёнок двусторонние, корень [start] взят всегда.
 * Здесь решается, можно ли взять узел и не повиснет ли часть дерева при откате.
 */
class AtlasGraph(nodes: Collection<AtlasNode>) {
    val byCode: Map<String, AtlasNode> = nodes.associateBy { it.code }

    val start: String = nodes.first { it.kind == EnumAtlasNodeKind.START }.code

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

    /** Смежен ли узел с корнем или хоть одним из [allocated]. */
    fun isAdjacentTo(code: String, allocated: Collection<String>): Boolean =
        neighbours(code).any { it == start || it in allocated }

    /** Достижим ли каждый из [allocated] от корня по одним только взятым узлам. */
    fun isConnected(allocated: Collection<String>): Boolean {
        val remaining = allocated.toHashSet().apply { remove(start) }
        val queue = ArrayDeque(listOf(start))
        while (queue.isNotEmpty() && remaining.isNotEmpty()) {
            neighbours(queue.removeFirst()).forEach { next -> if (remaining.remove(next)) queue.addLast(next) }
        }
        return remaining.isEmpty()
    }
}

/**
 * Очки атласа. Их приносят достижения на картах - ключи вида `<вид>:<код карты>`: выход ([EXIT]),
 * выход с редкой картой ([RARE]) и страж Ваал-зоны ([VAAL]). Каждый ключ засчитывается один раз,
 * сколько очков он стоит, говорит `points` файла.
 */
object AtlasPoints {
    const val EXIT = "exit"
    const val RARE = "rare"
    const val VAAL = "vaal"
    val KINDS = setOf(EXIT, RARE, VAAL)

    fun key(kind: String, mapCode: String) = "$kind:$mapCode"

    /**
     * Засчитывает достижение [kind] на карте [mapCode] в [earned], если его там ещё не было;
     * `true` - ключ новый, и героя надо записать.
     */
    fun earn(earned: MutableCollection<String>, kind: String, mapCode: String): Boolean =
        key(kind, mapCode).let { key -> key !in earned && earned.add(key) }

    /** Сколько очков принесли [earned] по правилу [rule]. */
    fun total(rule: Map<String, Int>, earned: Collection<String>): Int =
        earned.sumOf { rule[it.substringBefore(':')] ?: 0 }

    /** Свободные очки: заработанное минус взятые узлы; корень бесплатен и в [allocated] не лежит. */
    fun available(rule: Map<String, Int>, earned: Collection<String>, allocated: Collection<String>): Int =
        total(rule, earned) - allocated.size
}

/** Проверки взятия и отката узла атласа. Чистые: граф и взятое приходят снаружи. */
object AtlasAllocation {

    fun requireAllocatable(graph: AtlasGraph, code: String, allocated: Collection<String>, available: Int, method: String = "allocateAtlas") {
        requireNode(graph, code, method)
        if (code == graph.start) throw AtlasExceptions.funExceptionStart(method, code)
        if (code in allocated) throw AtlasExceptions.funExceptionAlreadyTaken(method, code)
        if (!graph.isAdjacentTo(code, allocated)) throw AtlasExceptions.funExceptionNotConnected(method, code)
        if (available < 1) throw AtlasExceptions.funExceptionNoPoints(method, available.toString())
    }

    /** Откатить можно только узел, без которого остальные взятые по-прежнему достижимы от корня. */
    fun requireRefundable(graph: AtlasGraph, code: String, allocated: Collection<String>, method: String = "refundAtlas") {
        requireNode(graph, code, method)
        if (code == graph.start) throw AtlasExceptions.funExceptionStart(method, code)
        if (code !in allocated) throw AtlasExceptions.funExceptionNotTaken(method, code)
        if (!graph.isConnected(allocated - code)) throw AtlasExceptions.funExceptionWouldDetach(method, code)
    }

    private fun requireNode(graph: AtlasGraph, code: String, method: String) {
        graph.node(code) ?: throw AtlasExceptions.funExceptionNodeNotFound(method, code)
    }
}

/**
 * Что атлас героя даёт картам: прибавки взятых узлов, сложенные по характеристикам. Чистое
 * значение - кампания читает из него поправки, а клиенту [effects] уходят целиком.
 */
data class AtlasBonuses(val effects: Map<String, Double> = emptyMap()) {

    operator fun get(stat: EnumStatStock): Double = effects[stat.name] ?: 0.0

    val quantity get() = this[EnumStatStock.ATLAS_QUANTITY]
    val rarity get() = this[EnumStatStock.ATLAS_RARITY]
    val experience get() = this[EnumStatStock.ATLAS_EXPERIENCE]
    val vaalReward get() = this[EnumStatStock.ATLAS_VAAL_REWARD]
    val vaalMinMods get() = this[EnumStatStock.ATLAS_VAAL_MIN_MODS].toInt()
    val chests get() = this[EnumStatStock.ATLAS_CHESTS].toInt()

    /** Шанс выпадения карты с поправкой `ATLAS_MAP_DROP`. */
    fun mapChance(chance: Double) = chance * relative(EnumStatStock.ATLAS_MAP_DROP)

    /** Шанс уникалки с босса с поправкой `ATLAS_BOSS_UNIQUE`. */
    fun bossUniqueChance(chance: Double) = chance * relative(EnumStatStock.ATLAS_BOSS_UNIQUE)

    /** Часы до возвращения босса: `ATLAS_BOSS_RESPAWN` сокращает их, но не больше чем на [RESPAWN_CAP] процентов. */
    fun bossRespawnHours(hours: Double) = hours * (1 - this[EnumStatStock.ATLAS_BOSS_RESPAWN].coerceIn(0.0, RESPAWN_CAP) / 100)

    private fun relative(stat: EnumStatStock) = (1 + this[stat] / 100).coerceAtLeast(0.0)

    companion object {
        const val PREFIX = "ATLAS_"
        const val RESPAWN_CAP = 90.0
        val NONE = AtlasBonuses()

        fun of(graph: AtlasGraph, allocated: Collection<String>): AtlasBonuses {
            val effects = mutableMapOf<String, Double>()
            allocated.mapNotNull(graph::node).flatMap { it.effects }.forEach { effects.merge(it.stat, it.value, Double::plus) }
            return AtlasBonuses(effects)
        }
    }
}

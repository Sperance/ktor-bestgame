package features.logic.atlas

import application.enums.EnumStatStock
import base.exception.model.AtlasExceptions
import config.ContentResource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Вид узла атласа: корень, малый узел пути, заметный на развилке или в конце ветви и ключевой в глубине. */
enum class EnumAtlasNodeKind { START, SMALL, NOTABLE, KEYSTONE }

/** Одна прибавка узла: характеристика `ATLAS_*` из [EnumStatStock] и её значение. */
@Serializable
data class AtlasEffect(val stat: String, val value: Double)

/**
 * Узел атласа. [x] и [y] - раскладка для клиента: [y] растёт вверх от корня внизу (`y = 0`).
 * [parents] - узлы, из которых сюда ведёт путь; у корня их нет. Название - `atlas.node.<code>.name`,
 * строки эффектов клиент собирает по характеристикам.
 */
@Serializable
data class AtlasNode(
    val code: String,
    val x: Double,
    val y: Double,
    val kind: EnumAtlasNodeKind,
    val parents: List<String> = emptyList(),
    val effects: List<AtlasEffect> = emptyList(),
)

/** Цена отката узла золотом: [perNode] плюс [perLevel] за каждый уровень героя. */
@Serializable
data class AtlasRespec(val perNode: Long, val perLevel: Long = 0) {
    fun price(level: Int, nodes: Int): Long = (perNode + perLevel * level) * nodes
}

/**
 * Пассивное дерево атласа (с 0.60.0): [points] - сколько очков приносит каждый вид достижения
 * ([AtlasPoints.EXIT], [AtlasPoints.RARE], [AtlasPoints.VAAL]) на каждой карте, [respec] - цена отката.
 */
@Serializable
data class AtlasTree(val points: Map<String, Int>, val respec: AtlasRespec, val nodes: List<AtlasNode>)

/**
 * Атлас из `resources/content/atlas.json`. Файл читается и проверяется на старте, как кампания:
 * неизвестная характеристика, висящий родитель, цикл или узел, до которого не дойти от корня, -
 * ошибка старта, а не тихо урезанное дерево.
 */
object AtlasContent {

    const val FILE = "atlas.json"

    private val json = Json { ignoreUnknownKeys = true }

    val tree: AtlasTree by lazy { load(ContentResource.read(FILE)) }

    val graph: AtlasGraph by lazy { AtlasGraph(tree.nodes) }

    /** Читает файл сразу: битый атлас должен уронить старт, а не первый запрос героя. */
    fun initialize() {
        graph
    }

    fun load(text: String): AtlasTree = json.decodeFromString(AtlasTree.serializer(), text).also(::validate)

    /** Сумма прибавок взятых узлов героя - то, что атлас даёт картам. */
    fun bonuses(allocated: Collection<String>): AtlasBonuses = AtlasBonuses.of(graph, allocated)

    fun validate(tree: AtlasTree) {
        val method = "AtlasContent"
        fun fail(what: String): Nothing = throw AtlasExceptions.funExceptionContent(method, what)
        val stats = EnumStatStock.entries.map { it.name }.filter { it.startsWith(AtlasBonuses.PREFIX) }.toSet()

        tree.points.forEach { (kind, amount) -> if (kind !in AtlasPoints.KINDS || amount < 0) fail("points $kind") }
        if (tree.respec.perNode < 0 || tree.respec.perLevel < 0) fail("respec")
        val byCode = tree.nodes.associateBy { it.code }
        if (byCode.size != tree.nodes.size) fail("node codes")
        val starts = tree.nodes.filter { it.kind == EnumAtlasNodeKind.START }
        if (starts.size != 1 || starts.single().parents.isNotEmpty()) fail("exactly one START without parents")
        tree.nodes.forEach { node ->
            if (node.code.isBlank()) fail("blank node code")
            if (node.kind != EnumAtlasNodeKind.START && node.parents.isEmpty()) fail("node ${node.code} without parents")
            node.parents.forEach { if (it !in byCode || it == node.code) fail("parent $it of ${node.code}") }
            node.effects.forEach { if (it.stat !in stats) fail("stat ${it.stat} of ${node.code}") }
        }
        // Цикл по родителям: узел, который сам себе предок, не повесить ни на какую глубину.
        val done = HashSet<String>()
        val path = HashSet<String>()
        fun visit(code: String) {
            if (code in done) return
            if (!path.add(code)) fail("cycle through $code")
            byCode.getValue(code).parents.forEach(::visit)
            path.remove(code)
            done += code
        }
        tree.nodes.forEach { visit(it.code) }
        // Каждый узел достижим от корня по связям родитель - ребёнок.
        val graph = AtlasGraph(tree.nodes)
        if (!graph.isConnected(byCode.keys)) fail("nodes unreachable from ${graph.start}")
    }
}

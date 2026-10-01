import com.sperance.exileforge.rules.content.AtlasNode
import config.ContentStore
import org.junit.Test
import kotlin.math.hypot
import kotlin.test.assertTrue

/**
 * Правило владельца (1.41.0) и для Атласа: связи узлов не пересекаются и не проходят сквозь чужие узлы, узлы не сливаются.
 * Атлас (1.51.0) - созвездия механик и звёздные тропы между ними; связи без циклов по родителям, петли - через нескольких родителей.
 * Узлы разнесены (раскладка без слипаний на телефоне): любые два - не ближе [NODE_GAP] единиц, связь - не ближе [EDGE_GAP] к чужому узлу.
 */
class AtlasLayoutTest {
    private val nodes = ContentStore.load().index.atlas.nodes
    private val at = nodes.associateBy { it.code }
    private val edges = nodes.flatMap { node -> node.parents.map { at.getValue(it) to node } }

    private fun ccw(a: AtlasNode, b: AtlasNode, c: AtlasNode) = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

    private fun distance(p: AtlasNode, a: AtlasNode, b: AtlasNode): Double {
        val dx = b.x - a.x; val dy = b.y - a.y
        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / (dx * dx + dy * dy).coerceAtLeast(1e-9)).coerceIn(0.0, 1.0)
        return hypot(p.x - a.x - t * dx, p.y - a.y - t * dy)
    }

    @Test
    fun links_never_cross_nor_pass_through_nodes() {
        val crossing = edges.indices.flatMap { i -> (i + 1 until edges.size).map { edges[i] to edges[it] } }.filter { (e, f) ->
            setOf(e.first, e.second, f.first, f.second).size == 4 &&
                ccw(e.first, e.second, f.first) * ccw(e.first, e.second, f.second) < 0 && ccw(f.first, f.second, e.first) * ccw(f.first, f.second, e.second) < 0
        }
        val through = edges.flatMap { (a, b) -> nodes.filter { it !== a && it !== b && distance(it, a, b) < EDGE_GAP }.map { "${a.code}-${b.code} через ${it.code}" } }
        assertTrue(crossing.isEmpty(), "связи Атласа пересекаются: ${crossing.size}")
        assertTrue(through.isEmpty(), "связь Атласа сквозь узел: ${through.take(5)}")
    }

    @Test
    fun nodes_keep_apart() {
        val close = nodes.indices.flatMap { i -> (i + 1 until nodes.size).map { nodes[i] to nodes[it] } }
            .filter { (a, b) -> hypot(a.x - b.x, a.y - b.y) < NODE_GAP }.map { (a, b) -> "${a.code}/${b.code}" }
        assertTrue(close.isEmpty(), "узлы Атласа слишком близко: ${close.take(5)}")
    }

    private companion object {
        const val EDGE_GAP = 2.0
        const val NODE_GAP = 4.0
    }
}

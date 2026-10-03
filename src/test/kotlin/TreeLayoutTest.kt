import com.sperance.exileforge.rules.content.SkillNodeType
import com.sperance.exileforge.rules.content.TreeNode
import config.ContentStore
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.test.assertTrue

/**
 * Правило владельца (1.37.0): на карте дерева связи не пересекаются, не проходят сквозь чужие узлы, узлы не сливаются
 * и связи одного узла не ложатся друг на друга. Радиусы и зазоры - те же, что у раскладчика `scripts/layout_tree.py`.
 */
class TreeLayoutTest {
    private val nodes = ContentStore.load().index.tree.byCode.values.toList()
    private val edges = nodes.flatMap { node -> node.connections.map { setOf(node.code, it) } }.distinct().map { it.toList() }
    private val at = nodes.associateBy { it.code }

    private fun TreeNode.radius() = when (type) {
        SkillNodeType.SMALL -> 15.0
        SkillNodeType.ATTRIBUTE -> 16.0
        SkillNodeType.START -> 30.0
        SkillNodeType.KEYSTONE -> 33.0
        SkillNodeType.NOTABLE, SkillNodeType.MASTERY, SkillNodeType.JEWEL_SOCKET -> 25.0
    }

    private fun ccw(a: TreeNode, b: TreeNode, c: TreeNode) = (b.x - a.x).toDouble() * (c.y - a.y) - (b.y - a.y).toDouble() * (c.x - a.x)

    private fun distance(p: TreeNode, a: TreeNode, b: TreeNode): Double {
        val dx = (b.x - a.x).toDouble()
        val dy = (b.y - a.y).toDouble()
        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / (dx * dx + dy * dy).coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
        return hypot(p.x - a.x - t * dx, p.y - a.y - t * dy)
    }

    @Test
    fun links_never_cross() {
        val crossing = edges.indices.flatMap { i -> (i + 1 until edges.size).map { j -> edges[i] to edges[j] } }.filter { (e, f) ->
            (e + f).toSet().size == 4 && at.getValue(e[0]).let { a ->
                at.getValue(e[1]).let { b ->
                    at.getValue(f[0]).let { c ->
                        at.getValue(f[1]).let { d ->
                            ccw(a, b, c) * ccw(a, b, d) < 0 && ccw(c, d, a) * ccw(c, d, b) < 0
                        }
                    }
                }
            }
        }
        assertTrue(crossing.isEmpty(), "связи пересекаются: ${crossing.size}, например ${crossing.take(5)}")
    }

    @Test
    fun links_pass_no_foreign_node() {
        val through = edges.flatMap { (a, b) ->
            nodes.filter { it.code != a && it.code != b && distance(it, at.getValue(a), at.getValue(b)) < it.radius() + EDGE_GAP }.map { "$a-$b через ${it.code}" }
        }
        assertTrue(through.isEmpty(), "связь проходит сквозь узел: ${through.size}, например ${through.take(5)}")
    }

    @Test
    fun nodes_keep_apart_and_links_fan_out() {
        val close = nodes.indices.flatMap { i -> (i + 1 until nodes.size).map { nodes[i] to nodes[it] } }
            .filter { (a, b) -> hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()) < a.radius() + b.radius() + NODE_GAP }
            .map { (a, b) -> "${a.code}/${b.code}" }
        val neighbours = edges.flatMap { listOf(it[0] to it[1], it[1] to it[0]) }.groupBy({ it.first }, { at.getValue(it.second) })
        val folded = neighbours.filter { (code, others) ->
            val node = at.getValue(code)
            val angles = others.map { Math.toDegrees(atan2((it.y - node.y).toDouble(), (it.x - node.x).toDouble())) }.sorted()
            angles.size > 1 && (angles.zipWithNext { a, b -> b - a } + (angles.first() + 360 - angles.last())).any { it < MIN_ANGLE }
        }.keys
        assertTrue(close.isEmpty(), "узлы слишком близко: ${close.size}, например ${close.take(5)}")
        assertTrue(folded.isEmpty(), "связи узла ложатся друг на друга: ${folded.take(5)}")
    }

    private companion object {
        const val EDGE_GAP = 12.0
        const val NODE_GAP = 40.0
        const val MIN_ANGLE = 12.0
    }
}

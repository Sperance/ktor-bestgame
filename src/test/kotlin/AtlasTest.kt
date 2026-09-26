import base.exception.model.AtlasExceptions
import features.logic.atlas.AtlasAllocation
import features.logic.atlas.AtlasBonuses
import features.logic.atlas.AtlasContent
import features.logic.atlas.AtlasEffect
import features.logic.atlas.AtlasGraph
import features.logic.atlas.AtlasNode
import features.logic.atlas.AtlasPoints
import features.logic.atlas.EnumAtlasNodeKind
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Атлас (0.60.0): очки, взятие по соседству, откат без обрыва дерева и целость `atlas.json`. */
class AtlasTest {

    private fun node(code: String, vararg parents: String, kind: EnumAtlasNodeKind = EnumAtlasNodeKind.SMALL) =
        AtlasNode(code, 0.0, 0.0, if (parents.isEmpty()) EnumAtlasNodeKind.START else kind, parents.toList(),
            listOf(AtlasEffect("ATLAS_QUANTITY", 1.0)).takeIf { parents.isNotEmpty() }.orEmpty())

    // S - A - B - C, и от A ещё D
    private val graph = AtlasGraph(listOf(node("S"), node("A", "S"), node("B", "A"), node("C", "B"), node("D", "A")))

    @Test
    fun points_count_each_achievement_once_and_the_start_is_free() {
        val rule = mapOf(AtlasPoints.BOSS to 1, AtlasPoints.RARE to 2, AtlasPoints.VAAL to 1)
        val earned = mutableListOf<String>()
        assertTrue(AtlasPoints.earn(earned, AtlasPoints.BOSS, "M1"))
        assertTrue(!AtlasPoints.earn(earned, AtlasPoints.BOSS, "M1"), "the same boss counted twice")
        AtlasPoints.earn(earned, AtlasPoints.RARE, "M1")
        AtlasPoints.earn(earned, AtlasPoints.VAAL, "M2")
        assertEquals(4, AtlasPoints.total(rule, earned))
        assertEquals(2, AtlasPoints.available(rule, earned, listOf("A", "B")))
        assertEquals(2.0, AtlasBonuses.of(graph, listOf("A", "B")).quantity)
    }

    @Test
    fun a_node_is_taken_only_next_to_the_allocated_ones() {
        AtlasAllocation.requireAllocatable(graph, "A", emptyList(), 1)
        assertFailsWith<AtlasExceptions.AtlasException> { AtlasAllocation.requireAllocatable(graph, "B", emptyList(), 1) }
        assertFailsWith<AtlasExceptions.AtlasException> { AtlasAllocation.requireAllocatable(graph, "B", listOf("A"), 0) }
        assertFailsWith<AtlasExceptions.AtlasException> { AtlasAllocation.requireAllocatable(graph, "A", listOf("A"), 1) }
        assertFailsWith<AtlasExceptions.AtlasException> { AtlasAllocation.requireAllocatable(graph, "S", emptyList(), 1) }
        AtlasAllocation.requireAllocatable(graph, "B", listOf("A"), 1)
    }

    @Test
    fun a_refund_never_detaches_the_rest() {
        val taken = listOf("A", "B", "C", "D")
        AtlasAllocation.requireRefundable(graph, "C", taken)
        AtlasAllocation.requireRefundable(graph, "D", taken)
        assertFailsWith<AtlasExceptions.AtlasException> { AtlasAllocation.requireRefundable(graph, "B", taken) }
        assertFailsWith<AtlasExceptions.AtlasException> { AtlasAllocation.requireRefundable(graph, "A", listOf("A", "D")) }
        assertFailsWith<AtlasExceptions.AtlasException> { AtlasAllocation.requireRefundable(graph, "S", taken) }
    }

    @Test
    fun the_atlas_file_is_whole() {
        val tree = AtlasContent.tree
        assertTrue(tree.nodes.size in 70..100, "the atlas has ${tree.nodes.size} nodes")
        assertTrue(AtlasContent.graph.isConnected(tree.nodes.map { it.code }))
        assertTrue(tree.nodes.count { it.kind == EnumAtlasNodeKind.KEYSTONE } in 2..4)
    }

    @Test
    fun a_broken_atlas_fails_to_load() {
        fun file(vararg nodes: String) = """{"points":{"boss":1},"respec":{"perNode":1},"nodes":[${nodes.joinToString(",")}]}"""
        val start = """{"code":"S","x":0,"y":0,"kind":"START"}"""
        listOf(
            file(start, """{"code":"A","x":0,"y":1,"kind":"SMALL","parents":["S"],"effects":[{"stat":"STOCK_QUANTITY","value":1}]}"""),
            file(start, """{"code":"A","x":0,"y":1,"kind":"SMALL","parents":["X"]}"""),
            file(start, """{"code":"A","x":0,"y":1,"kind":"SMALL","parents":["B"]}""", """{"code":"B","x":0,"y":2,"kind":"SMALL","parents":["A"]}"""),
            file(start, """{"code":"A","x":0,"y":1,"kind":"SMALL"}"""),
        ).forEach { text -> assertFailsWith<AtlasExceptions.AtlasException>(text) { AtlasContent.load(text) } }
    }
}

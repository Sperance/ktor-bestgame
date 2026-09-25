import application.enums.EnumSkillNodeType
import base.exception.model.SkillTreeExceptions
import config.ModifierSeeder
import config.SkillTreeSeeder
import features.logic.modifiers.Modifier
import features.logic.skilltree.SkillTreeAllocation
import features.logic.skilltree.SkillTreeGraph
import features.logic.skilltree.SkillTreeNode
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Мастерства и атрибутные узлы (0.52.0): выбор обязателен, а мастерство - лист, через него пути нет. */
class SkillTreeTest {

    private fun node(code: String, type: EnumSkillNodeType, vararg links: String, options: Int = 0) = SkillTreeNode(
        code = code, type = type, connections = links.toMutableList(),
        options = List(options) { listOf(Modifier.passive("m$it", listOf(1.0))) },
    )

    // START - A(notable) - M(mastery) - B(notable); кольцо A - R - B идёт в обход
    private val graph = SkillTreeGraph(listOf(
        node("START", EnumSkillNodeType.START, "A"),
        node("A", EnumSkillNodeType.NOTABLE, "M", "R"),
        node("M", EnumSkillNodeType.MASTERY, "B", options = 3),
        node("R", EnumSkillNodeType.SMALL, "B"),
        node("B", EnumSkillNodeType.NOTABLE),
    ))

    @Test
    fun a_mastery_leads_nowhere() {
        assertTrue(graph.isAdjacentTo("M", listOf("START", "A")))
        assertFalse(graph.isAdjacentTo("B", listOf("START", "A", "M")), "the mastery bridged two notables")
        assertFalse(graph.isConnected(listOf("START", "A", "M", "B")), "B hangs on the mastery alone")
        assertTrue(graph.isConnected(listOf("START", "A", "M", "R", "B")))
    }

    @Test
    fun a_choosing_node_needs_exactly_one_of_its_options() {
        val mastery = graph.node("M")!!
        val taken = listOf("START", "A")
        assertFailsWith<SkillTreeExceptions.SkillTreeException> { SkillTreeAllocation.requireAllocatable(graph, mastery, taken, "START", 5) }
        assertFailsWith<SkillTreeExceptions.SkillTreeException> { SkillTreeAllocation.requireAllocatable(graph, mastery, taken, "START", 5, choice = 3) }
        SkillTreeAllocation.requireAllocatable(graph, mastery, taken, "START", 5, choice = 2)
        assertFailsWith<SkillTreeExceptions.SkillTreeException> { SkillTreeAllocation.requireAllocatable(graph, graph.node("R")!!, taken, "START", 5, choice = 0) }
    }

    @Test
    fun the_seeded_tree_is_whole() {
        val tree = SkillTreeSeeder.seed(ModifierSeeder.seedDefinitions())
        val seeded = SkillTreeGraph(tree)
        assertTrue(tree.size > 400, "the tree shrank: ${tree.size}")
        assertTrue(seeded.isConnected(tree.map { it.code }), "some nodes cannot be reached from the starts")
        assertTrue(tree.filter { it.type == EnumSkillNodeType.MASTERY || it.type == EnumSkillNodeType.ATTRIBUTE }.all { it.options.size >= 2 })
    }
}

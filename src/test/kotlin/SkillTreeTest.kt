import application.enums.EnumModifierOperation
import application.enums.EnumSkillNodeType
import config.ModifierSeeder
import config.SkillTreeSeeder
import config.UniqueEquipmentSeeder
import features.logic.modifiers.ModifierDefinition
import features.logic.modifiers.ModifierMath
import features.logic.skilltree.SkillTreeGraph
import features.logic.skilltree.SkillTreeNode
import org.junit.Test

/**
 * Дерево навыков: целостность данных и правила обхода.
 * Mongo не нужна - и сидер, и граф чистые.
 */
class SkillTreeTest {

    private val definitions: List<ModifierDefinition> =
        ModifierSeeder.seedDefinitions() + UniqueEquipmentSeeder.seedDefinitions()

    private val tree: List<SkillTreeNode> = SkillTreeSeeder.seed(definitions)

    private val byCode = tree.associateBy { it.code }

    // ==================== Данные ====================

    @Test
    fun node_codes_and_ids_are_unique_and_stable() {
        assert(tree.map { it.code }.toSet().size == tree.size) { "Duplicate node codes" }
        assert(tree.map { it._id }.toSet().size == tree.size) { "Duplicate node ids" }

        val again = SkillTreeSeeder.seed(definitions).associate { it.code to it._id }
        assert(again == tree.associate { it.code to it._id }) { "Node ids changed between seeds" }
    }

    @Test
    fun every_bonus_points_at_a_known_definition() {
        val definitionIds = definitions.associateBy { it._id }

        tree.forEach { node ->
            node.params.forEach { bonus ->
                val definition = definitionIds[bonus.modifierId]
                assert(definition != null) { "${node.code} references an unknown modifier" }
                assert(bonus.values.size == definition!!.effects.size) {
                    "${node.code}: ${definition.effects.size} effects, ${bonus.values.size} values"
                }
                assert(!bonus.isRolled()) { "${node.code}: a passive must carry no tier" }
            }
        }
    }

    @Test
    fun only_start_nodes_are_free_and_they_carry_no_bonuses() {
        tree.forEach { node ->
            if (node.type == EnumSkillNodeType.START) {
                assert(node.cost == 0) { "${node.code} is a START node but costs ${node.cost}" }
                assert(node.params.isEmpty()) { "${node.code} is a START node but grants bonuses" }
            } else {
                assert(node.cost > 0) { "${node.code} costs nothing" }
                assert(node.params.isNotEmpty()) { "${node.code} grants nothing" }
            }
        }
    }

    @Test
    fun keystones_carry_a_downside() {
        val keystones = tree.filter { it.type == EnumSkillNodeType.KEYSTONE }
        assert(keystones.isNotEmpty()) { "No keystones in the tree" }

        val definitionIds = definitions.associateBy { it._id }
        keystones.forEach { node ->
            val operations = node.params
                .flatMap { definitionIds.getValue(it.modifierId).effects }
                .map { it.operation }

            // Кейстоун меняет правила: либо заменяет стат, либо даёт мультипликативный бонус
            val changesRules = operations.any {
                it == EnumModifierOperation.SET || it == EnumModifierOperation.MORE
            }
            assert(changesRules) { "${node.code} is a keystone but behaves like a small node" }
        }
    }

    // ==================== Граф ====================

    @Test
    fun connections_are_symmetric_and_point_at_existing_nodes() {
        tree.forEach { node ->
            node.connections.forEach { other ->
                val neighbour = byCode[other]
                assert(neighbour != null) { "${node.code} links to unknown node $other" }
                assert(node.code in neighbour!!.connections) {
                    "${node.code} -> $other is not mirrored back"
                }
            }
        }
    }

    @Test
    fun every_node_is_reachable_from_some_start() {
        val starts = tree.filter { it.type == EnumSkillNodeType.START }.map { it.code }
        assert(starts.isNotEmpty()) { "The tree has no START node" }

        val seen = starts.toMutableSet()
        val queue = ArrayDeque(starts)
        while (queue.isNotEmpty()) {
            SkillTreeGraph.neighbours(tree, queue.removeFirst())
                .filter { seen.add(it) }
                .forEach { queue.addLast(it) }
        }

        val unreachable = tree.map { it.code }.toSet() - seen
        assert(unreachable.isEmpty()) { "Unreachable nodes: $unreachable" }
    }

    @Test
    fun a_node_can_only_be_taken_next_to_a_taken_one() {
        val start = "STR_START"
        val taken = mutableListOf(start)

        assert(!SkillTreeGraph.isAdjacentTo(tree, "STR_MIGHT_3", taken)) {
            "A node three steps away was reachable from the start alone"
        }

        listOf("STR_MIGHT_1", "STR_MIGHT_2", "STR_MIGHT_3").forEach { code ->
            assert(SkillTreeGraph.isAdjacentTo(tree, code, taken)) { "$code was not adjacent to $taken" }
            taken.add(code)
        }
    }

    @Test
    fun refunding_a_middle_node_would_detach_the_branch() {
        val path = listOf("STR_START", "STR_MIGHT_1", "STR_MIGHT_2", "STR_MIGHT_3")
        assert(SkillTreeGraph.isConnected(tree, path)) { "A straight path is not connected" }

        val withoutMiddle = path.filterNot { it == "STR_MIGHT_2" }
        assert(!SkillTreeGraph.isConnected(tree, withoutMiddle)) {
            "Removing a middle node left the branch connected"
        }

        val withoutLeaf = path.filterNot { it == "STR_MIGHT_3" }
        assert(SkillTreeGraph.isConnected(tree, withoutLeaf)) { "Removing a leaf broke the branch" }
    }

    @Test
    fun a_set_without_a_start_is_never_connected() {
        assert(!SkillTreeGraph.isConnected(tree, listOf("STR_MIGHT_1", "STR_MIGHT_2"))) {
            "A branch with no start counted as connected"
        }
        assert(SkillTreeGraph.isConnected(tree, emptyList())) { "An empty tree must be connected" }
    }

    @Test
    fun the_three_starts_are_linked_through_the_ring() {
        val ring = listOf(
            "STR_START", "RING_STR_DEX", "KEYSTONE_IRON_REFLEXES", "DEX_START",
            "RING_DEX_INT", "INT_START", "RING_INT_STR"
        )
        assert(SkillTreeGraph.isConnected(tree, ring)) { "The ring does not link the three starts" }
    }
}

/**
 * Формула свода модификаторов POE.
 */
class ModifierMathTest {

    private fun add(value: Double) = EnumModifierOperation.ADD to value
    private fun increased(value: Double) = EnumModifierOperation.INCREASED to value
    private fun more(value: Double) = EnumModifierOperation.MORE to value
    private fun set(value: Double) = EnumModifierOperation.SET to value

    @Test
    fun no_modifiers_leave_the_base_alone() {
        assert(ModifierMath.apply(100.0, emptyList()) == 100.0)
    }

    @Test
    fun add_modifiers_are_summed() {
        assert(ModifierMath.apply(100.0, listOf(add(20.0), add(30.0))) == 150.0)
    }

    @Test
    fun increased_modifiers_are_additive_between_themselves() {
        // 100 * (1 + 0.30 + 0.20) = 150, а не 100 * 1.3 * 1.2 = 156
        assert(ModifierMath.apply(100.0, listOf(increased(30.0), increased(20.0))) == 150.0)
    }

    @Test
    fun more_modifiers_are_multiplicative() {
        // 100 * 1.3 * 1.2 = 156
        assert(ModifierMath.apply(100.0, listOf(more(30.0), more(20.0))) == 156.0)
    }

    @Test
    fun negative_more_reduces_the_result() {
        assert(ModifierMath.apply(100.0, listOf(more(-50.0))) == 50.0)
    }

    @Test
    fun the_whole_formula_applies_in_order() {
        // (100 + 50) * (1 + 0.20) * 1.10 = 198
        val result = ModifierMath.apply(100.0, listOf(add(50.0), increased(20.0), more(10.0)))
        assert(result == 198.0) { "expected 198.0, got $result" }
    }

    @Test
    fun set_overrides_everything_else() {
        // Chaos Inoculation: здоровье равно единице, сколько бы его ни давала экипировка
        val result = ModifierMath.apply(100.0, listOf(add(500.0), increased(200.0), more(50.0), set(1.0)))
        assert(result == 1.0) { "expected 1.0, got $result" }
    }

    @Test
    fun the_last_set_wins() {
        assert(ModifierMath.apply(100.0, listOf(set(5.0), set(0.0))) == 0.0)
    }
}

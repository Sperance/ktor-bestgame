import application.enums.EnumModifierOperation
import application.enums.EnumSkillNodeType
import config.ModifierSeeder
import config.ProgressionSeeder
import config.SkillTreeSeeder
import config.UniqueEquipmentSeeder
import features.logic.modifiers.ModifierDefinition
import features.logic.modifiers.ModifierMath
import base.exception.model.SkillTreeExceptions
import features.logic.skilltree.SkillTreeAllocation
import features.logic.skilltree.SkillTreeGraph
import features.logic.skilltree.SkillTreeNode
import org.junit.Assert.assertThrows
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

    @Test
    fun nodes_do_not_stand_on_top_of_each_other() {
        val places = tree.groupBy { it.positionX to it.positionY }.filterValues { it.size > 1 }
        assert(places.isEmpty()) { "Nodes share a position: ${places.values.map { group -> group.map { it.code } }}" }
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
    fun every_class_has_its_own_start_node() {
        val starts = tree.filter { it.type == EnumSkillNodeType.START }
        val expected = ProgressionSeeder.seedClasses(definitions).map { it.startNodeCode }.toSet()

        assert(starts.size == 7) { "expected seven starts, got ${starts.map { it.code }}" }
        assert(starts.map { it.code }.toSet() == expected) {
            "tree starts ${starts.map { it.code }} do not match class starts $expected"
        }
    }

    @Test
    fun the_ring_links_every_class_area() {
        val ring = tree.filter { it.code.startsWith("RING_") }.map { it.code }
        assert(ring.size == 6) { "expected six ring nodes, got $ring" }

        // Шесть областей стоят по кругу и держатся за кольцо напрямую
        val outer = tree
            .filter { it.type == EnumSkillNodeType.START && it.code != "SCION_START" }
            .map { it.code }
        assert(outer.size == 6) { "expected six outer starts, got $outer" }
        assert(SkillTreeGraph.isConnected(tree, ring + outer)) { "the ring does not link the class areas" }

        // Скион стоит в центре круга и выходит на кольцо своей веткой
        val scionPath = listOf(
            "SCION_START", "SCION_HUNTER_1", "SCION_HUNTER_2", "SCION_HUNTER_NOTABLE", "RING_DEX_INT_DEX"
        )
        assert(SkillTreeGraph.isConnected(tree, scionPath)) { "the Scion cannot reach the ring" }
    }

    @Test
    fun a_class_cannot_walk_into_a_foreign_area_without_the_ring() {
        // Единственный путь в чужую область - через кольцо: стартовый узел
        // другого класса взять нельзя. Закрываем кольцо и проверяем,
        // что область Мародёра замыкается на себе
        val reachable = reachableFrom("STR_START", blocked = ringCodes())

        assert("STR_MIGHT_NOTABLE" in reachable) { "the Marauder cannot reach its own notable" }
        assert("DEX_SWIFT_1" !in reachable) { "the Marauder reached the Ranger area without the ring" }
    }

    @Test
    fun the_scion_reaches_every_class_area_from_the_centre() {
        // Скион стоит в центре: через кольцо ему должны быть доступны
        // ветки всех шести областей, кроме их стартовых узлов
        val reachable = reachableFrom("SCION_START")

        listOf(
            "STR_MIGHT_1", "DEX_SWIFT_1", "INT_ARCANE_1",
            "STR_DEX_ARENA_1", "STR_INT_DEVOTION_1", "DEX_INT_TRICKERY_1"
        ).forEach { code ->
            assert(code in reachable) { "the Scion cannot reach $code" }
        }
    }

    @Test
    fun the_tree_costs_more_than_a_character_can_ever_spend() {
        val total = tree.sumOf { it.cost }
        val points = ProgressionSeeder.seedLevels().sumOf { it.skillPoints }

        assert(total > points) { "the whole tree costs $total and a character gets $points: there is nothing to choose" }
    }

    // ==================== Правила прокачки ====================

    private fun allocate(code: String, taken: List<String>, start: String = "STR_START", available: Int = 10) =
        SkillTreeAllocation.requireAllocatable(tree, byCode.getValue(code), taken, start, available)

    private fun refund(code: String, taken: List<String>) =
        SkillTreeAllocation.requireRefundable(tree, byCode.getValue(code), taken)

    @Test
    fun a_neighbour_of_a_taken_node_can_be_taken() {
        allocate("STR_MIGHT_1", listOf("STR_START"))
        allocate("STR_MIGHT_2", listOf("STR_START", "STR_MIGHT_1"))
    }

    @Test
    fun a_node_away_from_the_taken_ones_cannot_be_taken() {
        assertThrows(SkillTreeExceptions.SkillTreeException::class.java) {
            allocate("STR_MIGHT_3", listOf("STR_START"))
        }
    }

    @Test
    fun a_node_already_taken_cannot_be_taken_twice() {
        assertThrows(SkillTreeExceptions.SkillTreeException::class.java) {
            allocate("STR_MIGHT_1", listOf("STR_START", "STR_MIGHT_1"))
        }
    }

    @Test
    fun a_node_cannot_be_taken_without_points() {
        assertThrows(SkillTreeExceptions.SkillTreeException::class.java) {
            allocate("STR_MIGHT_1", listOf("STR_START"), available = 0)
        }
    }

    @Test
    fun a_character_cannot_start_from_a_foreign_class() {
        assertThrows(SkillTreeExceptions.SkillTreeException::class.java) {
            allocate("DEX_START", taken = emptyList())
        }
    }

    @Test
    fun a_second_start_node_cannot_be_taken() {
        // Стартовый узел уже есть с момента создания персонажа
        assertThrows(SkillTreeExceptions.SkillTreeException::class.java) {
            allocate("DEX_START", listOf("STR_START"))
        }
    }

    @Test
    fun a_leaf_is_refunded_and_a_middle_node_is_not() {
        val taken = listOf("STR_START", "STR_MIGHT_1", "STR_MIGHT_2")

        refund("STR_MIGHT_2", taken)
        assertThrows(SkillTreeExceptions.SkillTreeException::class.java) {
            refund("STR_MIGHT_1", taken)
        }
    }

    @Test
    fun the_start_node_is_refunded_only_by_a_full_reset() {
        assertThrows(SkillTreeExceptions.SkillTreeException::class.java) {
            refund("STR_START", listOf("STR_START", "STR_MIGHT_1"))
        }
    }

    @Test
    fun spending_counts_only_nodes_that_are_still_in_the_tree() {
        // Стартовый узел бесплатен, малый стоит очко, пропавший из дерева - ничего
        val spent = SkillTreeAllocation.spent(tree, listOf("STR_START", "STR_MIGHT_1", "GONE_FROM_THE_TREE"))
        assert(spent == 1) { "got $spent" }
    }

    private fun ringCodes(): Set<String> = tree.filter { it.code.startsWith("RING_") }.map { it.code }.toSet()

    /**
     * Куда персонаж может дойти от своего старта.
     *
     * Чужие стартовые узлы непроходимы: взять их нельзя, а значит и пройти
     * сквозь них тоже. Через [blocked] закрываются и другие узлы.
     */
    private fun reachableFrom(start: String, blocked: Set<String> = emptySet()): Set<String> {
        val closed = blocked + tree
            .filter { it.type == EnumSkillNodeType.START && it.code != start }
            .map { it.code }

        val seen = mutableSetOf(start)
        val queue = ArrayDeque(listOf(start))

        while (queue.isNotEmpty()) {
            SkillTreeGraph.neighbours(tree, queue.removeFirst())
                .filter { it !in closed && seen.add(it) }
                .forEach { queue.addLast(it) }
        }

        return seen
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

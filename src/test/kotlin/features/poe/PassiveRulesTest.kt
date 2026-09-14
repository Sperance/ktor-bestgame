package features.poe

import kotlin.test.*
import ru.descend.features.passives.domain.*
import ru.descend.features.passives.model.*
import ru.descend.features.character.domain.*
import ru.descend.features.character.model.*
import ru.descend.features.poe.catalog.*
import ru.descend.features.poe.domain.*
import ru.descend.shared.http.ApiFailure
import kotlinx.serialization.json.*

class PassiveRulesTest {
    private val tree = PassiveTreeSeed.tree
    private val rules = PassiveRules(tree)
    @Test fun sharedTreeHasBonusesOnEveryNodeAndLargeNodes() {
        assertEquals(115, tree.nodes.size)
        assertTrue(tree.nodes.all { it.effects.isNotEmpty() })
        assertEquals(6, tree.nodes.count { it.kind == PassiveNodeKind.KEYSTONE })
        assertEquals(18, tree.nodes.count { it.kind == PassiveNodeKind.NOTABLE })
        assertTrue(rules.connected(tree.nodes.map { it.id }.toSet()))
    }
    @Test fun budgetAndDependenciesCannotBeBypassed() {
        assertEquals(0, rules.budget(1)); assertEquals(99, rules.budget(1000))
        assertFailsWith<ApiFailure> { rules.transition(emptySet(), 1, PassiveAction.ALLOCATE, "origin") }
        assertFailsWith<ApiFailure> { rules.transition(emptySet(), 100, PassiveAction.ALLOCATE, "vitality_9") }
        val root = rules.transition(emptySet(), 2, PassiveAction.ALLOCATE, "origin")
        assertFailsWith<ApiFailure> { rules.transition(root, 2, PassiveAction.ALLOCATE, "vitality_1") }
        assertFailsWith<ApiFailure> { rules.transition(root, 10, PassiveAction.ALLOCATE, "origin") }
        assertFailsWith<ApiFailure> { rules.transition(root, 10, PassiveAction.ALLOCATE, "unknown") }
    }
    @Test fun refundPreservesConnectivityAndResetRestoresAllPoints() {
        val selected = setOf("origin", "vitality_1", "vitality_2")
        assertEquals(setOf("vitality_2"), rules.refundable(selected))
        assertFailsWith<ApiFailure> { rules.transition(selected, 10, PassiveAction.REFUND, "vitality_1") }
        assertEquals(emptySet(), rules.transition(selected, 10, PassiveAction.RESET, null))
    }
    @Test fun alternateRouteAllowsRefundOfANonLeafNode() {
        val nodes = listOf("a", "b", "c", "d").map { PassiveNode(it, it, "", PassiveNodeKind.SMALL, 0.0, 0.0, listOf(PassiveEffect("strength", PassiveOperation.FLAT, 1.0))) }
        val cycle = PassiveRules(PassiveTree(1, "cycle", "a", nodes, listOf(PassiveEdge("a","b"), PassiveEdge("b","c"), PassiveEdge("c","d"), PassiveEdge("d","a"))))
        assertTrue("b" in cycle.refundable(setOf("a", "b", "c", "d")))
        assertEquals(setOf("a","c","d"), cycle.transition(setOf("a","b","c","d"), 10, PassiveAction.REFUND, "b"))
    }
    @Test fun statsAreIsolatedAndFlatAndPercentBonusesUseOneResolver() {
        val catalog = PoeCatalog.bundled
        val original = Character("owner", "hero", level = 10)
        val selected = setOf("origin", "vitality_1", "vitality_2", "vitality_3")
        fun stats(c: Character) = CharacterStatsCalculator().calculate(c, emptyMap(), catalog)
        val before = stats(original)
        val after = stats(original.copy(passiveNodes = selected))
        assertEquals((before.values.getValue("maximum_life") + 21) * 1.08, after.values.getValue("maximum_life"), .00001)
        assertEquals(before, stats(original))
        val attack = stats(original.copy(passiveNodes = setOf("origin", "warfare_1")))
        assertTrue(attack.values.getValue("unarmed_dps") > before.values.getValue("unarmed_dps"))
    }
    @Test fun refundMustNotInvalidateEquippedItemRequirements() {
        val bundled = PoeCatalog.bundled
        val base = bundled.bases.entries.first { it.value.string("name") == "Rusted Sword" }
        val catalog = PoeCatalog(bundled.bases + (base.key to JsonObject(base.value + ("requirements" to buildJsonObject { put("strength", 25); put("level", 1) }))), bundled.mods)
        val sword = PoeInventory(catalog, PoeCrafting(catalog)).fromState(PoeCrafting(catalog).generate(base.key, 10, PoeRarity.NORMAL))
        val c = Character("owner", "hero", level = 10, passiveNodes = setOf("origin", "vitality_1", "vitality_1_side"), equipments = mutableListOf(sword), equipped = mapOf(EquipmentSlot.MAIN_HAND to sword.uuid))
        fun validate(c: Character) = EquipmentRules.validate(c, emptyMap(), catalog) { CharacterStatsCalculator().calculate(it, emptyMap(), catalog) }
        validate(c)
        assertFailsWith<ApiFailure> { validate(c.copy(passiveNodes = c.passiveNodes - "vitality_1_side")) }
    }
    @Test fun invalidCatalogAndKeystoneTradeoffsAreHandled() {
        assertFailsWith<IllegalArgumentException> { PassiveRules(tree.copy(edges = emptyList())) }
        val selected = setOf("origin") + (1..9).map { "vitality_$it" }
        val c = Character("owner", "hero", level = 20, passiveNodes = selected)
        val before = CharacterStatsCalculator().calculate(c, emptyMap(), PoeCatalog.bundled).values
        val after = CharacterStatsCalculator().calculate(c.copy(passiveNodes = selected + "vitality_keystone"), emptyMap(), PoeCatalog.bundled).values
        assertTrue(after.getValue("maximum_life") > before.getValue("maximum_life"))
        assertTrue(after.getValue("maximum_mana") < before.getValue("maximum_mana"))
    }
}

package features.poe

import features.logic.modifiers.*
import features.logic.stats.*
import kotlin.random.Random
import kotlin.test.*

class ModifierRegressionTest {
    @Test fun rolledValueReachesStatResolver() {
        val stat = StatId("strength")
        val modifier = Modifier("strength", listOf(ModifierValue(17.0)), 1, ModifierSource.SUFFIX)
        val resolved = ResolvedModifier("strength", 1, ModifierSource.SUFFIX,
            listOf(ModifierEffect.Stat(stat, ModifierOperation.FLAT, ValueExpression.ModifierValue(0))), 0, modifier)
        assertEquals(27.0, DefaultStatResolver(listOf(resolved), ValueExpressionEvaluator()).resolve(stat, StatContext(mapOf(stat to 10.0))))
    }
    @Test fun increasedAndReducedShareBucketAndBoundsApplyLast() {
        assertEquals(130.0, StatCalculation(base = 100.0, increased = .5, reduced = .2).calculate(), .0001)
        assertEquals(150.0, StatCalculation(base = 100.0, flat = 100.0, maximum = 150.0).calculate())
        assertEquals(0.0, StatCalculation(base = 100.0, set = 0.0, more = 5.0).calculate())
    }
    @Test fun zeroAndIneligibleWeightsAreNeverSelected() {
        val def = ModifierDefinition("test", "Test", ModifierSource.PREFIX, tiers = listOf(
            ModifierTier(1, 99, Int.MAX_VALUE, listOf(ValueRange(99.0, 99.0))),
            ModifierTier(2, 1, 0, listOf(ValueRange(0.0, 0.0))),
            ModifierTier(3, 1, 1, listOf(ValueRange(3.0, 3.0)))))
        val result = WeightedModifierGenerator(Random(1)).generate(listOf(def), 1, 3)
        assertEquals(1, result.size)
        assertEquals(3.0, result.single().value)
    }
    @Test fun resolverUsesPinnedRevisionAndRejectsDanglingReferences() {
        val first = ModifierDefinition("versioned", "First", ModifierSource.PREFIX,
            effects = listOf(ModifierEffect.Stat(StatId("life"), ModifierOperation.FLAT, ValueExpression.Constant(5.0))))
        val latest = first.copy(revision = 2,
            effects = listOf(ModifierEffect.Stat(StatId("life"), ModifierOperation.FLAT, ValueExpression.Constant(999.0))))
        val resolver = DefaultModifierResolver(InMemoryModifierDefinitionRegistry(listOf(first, latest)), DefaultConditionEvaluator())
        val rolled = Modifier("versioned", emptyList(), 1, ModifierSource.PREFIX, definitionRevision = 1)
        val resolved = resolver.resolve(listOf(rolled), ModifierContext())
        assertEquals(5.0, DefaultStatResolver(resolved, ValueExpressionEvaluator()).resolve(StatId("life"), StatContext()))
        assertFailsWith<IllegalArgumentException> { resolver.resolve(listOf(rolled.copy(definitionRevision = 3)), ModifierContext()) }
    }

}

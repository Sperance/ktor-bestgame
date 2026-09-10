package features.logic.modifiers

import kotlin.random.Random

/** Legacy definitions. PoE spawn/group rules are implemented by PoeCrafting. */
class WeightedModifierGenerator(private val random: Random = Random.Default) : ModifierGenerator {
    override fun generate(definitions: Collection<ModifierDefinition>, itemLevel: Int, count: Int): List<Modifier> {
        require(count in 0..1000) { "Invalid modifier count" }
        val candidates = definitions.filter { it.rollable && it.enabled }.flatMap { definition ->
            definition.tiers.filter { it.minItemLevel <= itemLevel && it.weight > 0 }.map { definition to it }
        }
        val result = mutableListOf<Modifier>()
        repeat(count) {
            val available = candidates.filter { (d, _) -> d.stackable || result.none { it.definitionId == d.id } }
            val total = available.sumOf { it.second.weight.toLong() }
            if (total == 0L) return result
            var ticket = random.nextLong(total)
            val (definition, tier) = available.first { ticket -= it.second.weight; ticket < 0 }
            result += Modifier(definition.id, tier.values.map {
                require(it.min.isFinite() && it.max.isFinite())
                ModifierValue(if (it.min == it.max) it.min else kotlin.math.round(random.nextDouble(it.min, it.max) * 10) / 10)
            }, tier.tier, definition.source, definition.tags, definition.revision)
        }
        return result
    }
}

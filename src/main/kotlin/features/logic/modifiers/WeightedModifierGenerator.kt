package features.logic.modifiers

import kotlin.random.Random

class WeightedModifierGenerator : ModifierGenerator {
    override fun generate(definitions: Collection<ModifierDefinition>, itemLevel: Int, count: Int): List<Modifier> {

        if (count <= 0) return emptyList()

        val available =
            definitions.filter { it.rollable }
                .filter { definition -> definition.tiers.any { it.minItemLevel <= itemLevel } }

        if (available.isEmpty()) return emptyList()

        val result = mutableListOf<Modifier>()

        repeat(count) {
            val definition = selectDefinition(available, result) ?: return@repeat
            val tier = selectTier(definition, itemLevel) ?: return@repeat
            val values = tier.values.map { ModifierValue(value = it.roll()) }

            result += Modifier(
                definitionId = definition.id,
                values = values,
                tier = tier.tier,
                source = definition.source,
                tags = definition.tags
            )
        }

        return result
    }

    private fun selectDefinition(definitions: List<ModifierDefinition>, alreadySelected: List<Modifier>): ModifierDefinition? {

        val candidates = definitions.filter { definition -> definition.stackable || alreadySelected.none { it.definitionId == definition.id } }
        if (candidates.isEmpty()) return null

        val weighted =
            candidates.flatMap { definition ->
                val weight = definition.tiers.sumOf { it.weight }
                List(weight.coerceAtLeast(1)) { definition } }

        return weighted.randomOrNull()
    }

    private fun selectTier(definition: ModifierDefinition, itemLevel: Int): ModifierTier? {

        val tiers = definition.tiers.filter { it.minItemLevel <= itemLevel }
        if (tiers.isEmpty()) return null

        val totalWeight = tiers.sumOf { it.weight }

        if (totalWeight <= 0) return tiers.maxByOrNull { it.tier }

        var roll = Random.nextInt(totalWeight)

        for (tier in tiers) {
            roll -= tier.weight
            if (roll < 0) return tier
        }

        return tiers.last()
    }
}
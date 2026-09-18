package features.logic.modifiers

import application.enums.EnumModifierOperation
import application.enums.IntEnumStat
import extensions.to1Digits
import features.caches.ModifierDefinitionCache
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Свод модификаторов в итоговые значения статов по формуле POE.
 *
 * Порядок применения задаёт [EnumModifierOperation]:
 *
 * `итог = (база + Σ ADD) * (1 + Σ INCREASED / 100) * Π (1 + MORE / 100)`
 *
 * - ADD складываются между собой;
 * - INCREASED складываются между собой и применяются одним множителем;
 * - MORE перемножаются, поэтому каждый такой модификатор ценнее предыдущего;
 * - SET заменяет базовое значение, остальные модификаторы считаются уже от него.
 */
object ModifierCalculator : KoinComponent {

    private val definitionCache: ModifierDefinitionCache by inject()

    /**
     * Итоговое значение одного стата.
     *
     * @param stat стат, который считаем
     * @param base базовое значение персонажа без экипировки
     * @param modifiers все модификаторы, влияющие на персонажа
     */
    fun calculate(stat: IntEnumStat, base: Double, modifiers: Collection<Modifier>): Double {
        val affecting = modifiers.mapNotNull { modifier ->
            val definition = definitionCache.findById(modifier.modifierId) ?: return@mapNotNull null
            if (definition.stat != stat) null else definition.operation to modifier.value
        }

        return apply(base, affecting)
    }

    /**
     * Итоговые значения всех статов, которых касаются переданные модификаторы.
     *
     * @param modifiers все модификаторы, влияющие на персонажа
     * @param base базовые значения статов, для отсутствующих берётся 0
     */
    fun calculate(
        modifiers: Collection<Modifier>,
        base: Map<IntEnumStat, Double> = emptyMap()
    ): Map<IntEnumStat, Double> {
        val grouped = mutableMapOf<IntEnumStat, MutableList<Pair<EnumModifierOperation, Double>>>()

        modifiers.forEach { modifier ->
            val definition = definitionCache.findById(modifier.modifierId) ?: return@forEach
            grouped.getOrPut(definition.stat) { mutableListOf() }
                .add(definition.operation to modifier.value)
        }

        val stats = grouped.keys + base.keys
        return stats.associateWith { stat ->
            apply(base[stat] ?: 0.0, grouped[stat] ?: emptyList())
        }
    }

    private fun apply(base: Double, operations: Collection<Pair<EnumModifierOperation, Double>>): Double {
        var result = operations
            .lastOrNull { it.first == EnumModifierOperation.SET }
            ?.second
            ?: base

        result += operations.filter { it.first == EnumModifierOperation.ADD }.sumOf { it.second }

        val increased = operations.filter { it.first == EnumModifierOperation.INCREASED }.sumOf { it.second }
        result *= (1.0 + increased / 100.0)

        operations.filter { it.first == EnumModifierOperation.MORE }.forEach { (_, value) ->
            result *= (1.0 + value / 100.0)
        }

        return result.to1Digits()
    }
}

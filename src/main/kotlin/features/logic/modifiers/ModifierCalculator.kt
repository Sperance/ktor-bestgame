package features.logic.modifiers

import application.enums.EnumModifierOperation
import application.enums.IntEnumStat
import features.caches.ModifierDefinitionCache
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Свод модификаторов в итоговые значения статов.
 *
 * Разбирает модификаторы по их описаниям из справочника и группирует
 * по статам, а саму арифметику POE считает [ModifierMath].
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
        val affecting = mutableListOf<Pair<EnumModifierOperation, Double>>()

        modifiers.forEach { modifier ->
            forEachEffect(modifier) { effect, value ->
                if (effect.stat == stat) affecting.add(effect.operation to value)
            }
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
            forEachEffect(modifier) { effect, value ->
                grouped.getOrPut(effect.stat) { mutableListOf() }.add(effect.operation to value)
            }
        }

        val stats = grouped.keys + base.keys
        return stats.associateWith { stat ->
            apply(base[stat] ?: 0.0, grouped[stat] ?: emptyList())
        }
    }

    /**
     * Разбирает зароленный модификатор на пары "эффект - выпавшее значение".
     *
     * У составного модификатора эффектов несколько, и значения идут
     * в том же порядке, что и эффекты описания.
     */
    private inline fun forEachEffect(modifier: Modifier, action: (ModifierEffect, Double) -> Unit) {
        val definition = definitionCache.findById(modifier.modifierId) ?: return
        definition.effects.forEachIndexed { index, effect ->
            action(effect, modifier.values.getOrNull(index) ?: return@forEachIndexed)
        }
    }

    private fun apply(base: Double, operations: Collection<Pair<EnumModifierOperation, Double>>): Double =
        ModifierMath.apply(base, operations)
}

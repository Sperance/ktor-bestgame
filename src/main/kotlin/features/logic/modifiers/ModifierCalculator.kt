package features.logic.modifiers

import application.enums.EnumModifierOperation
import application.enums.IntEnumStat
import features.caches.ModifierDefinitionCache
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Свод модификаторов в итоговые значения статов.
 *
 * Разворачивает модификаторы в плоские [StatOperation] по их описаниям,
 * раскладывает по статам и считает каждый стат в порядке [IntEnumStat.order].
 * Порядок гарантирует, что стат-источник конверсии уже посчитан к моменту,
 * когда до неё доходит очередь - поэтому отдельная топологическая сортировка
 * не нужна, а цикл конверсий невыразим.
 *
 * Саму арифметику POE считает [ModifierMath].
 */
object ModifierCalculator : KoinComponent {

    private val definitionCache: ModifierDefinitionCache by inject()

    /**
     * Итоговые значения всех статов, которых касаются переданные модификаторы.
     *
     * @param modifiers модификаторы, влияющие на персонажа
     * @param base базовые значения статов, для отсутствующих берётся 0
     */
    fun calculate(
        modifiers: Collection<Modifier>,
        base: Map<IntEnumStat, Double> = emptyMap()
    ): Map<IntEnumStat, Double> = compute(base, expand(modifiers))

    /**
     * Итоговое значение одного стата.
     *
     * Считается всё равно целиком: конверсии могут тянуть значение
     * из других статов, поэтому посчитать один в отрыве нельзя.
     */
    fun calculate(stat: IntEnumStat, base: Double, modifiers: Collection<Modifier>): Double =
        calculate(modifiers, mapOf(stat to base))[stat] ?: base

    /**
     * Считает статы по уже развёрнутым операциям.
     *
     * @param base базовые значения статов
     * @param operations плоские операции - от модификаторов, базы класса или свёрнутых предметов
     */
    fun compute(
        base: Map<IntEnumStat, Double>,
        operations: Collection<StatOperation>
    ): Map<IntEnumStat, Double> {
        val byStat = operations.groupBy { it.stat }
        val result = mutableMapOf<IntEnumStat, Double>()

        // Порядок статов и есть порядок вычисления: источник конверсии
        // всегда объявлен раньше своего приёмника
        (byStat.keys + base.keys).sortedBy { it.order }.forEach { stat ->
            val applied = byStat[stat].orEmpty().map { operation ->
                val source = operation.perStat?.let { result[it] ?: 0.0 } ?: 0.0
                operation.operation to operation.resolve(source)
            }
            result[stat] = ModifierMath.apply(base[stat] ?: 0.0, applied)
        }

        return result
    }

    /**
     * Что набор модификаторов даёт сам по себе, без базы - по строке на
     * характеристику и вид операции.
     *
     * Нужно там, где базы нет и быть не может: дерево навыков показывает свой
     * вклад, а не итог персонажа. Считать его через [compute] нельзя - от нулевой
     * базы INCREASED и MORE схлопываются в ноль (0 * 1.4 = 0), и процентные узлы
     * пропадают из ответа целиком.
     *
     * Каждый вид операции сворачивается своим правилом, тем же, что и в
     * [ModifierMath]: ADD и INCREASED складываются, MORE перемножаются и
     * возвращаются одним процентом, SET заменяет - остаётся последний.
     */
    fun contributions(operations: Collection<StatOperation>): List<StatContribution> =
        operations
            .groupBy { it.stat to it.operation }
            .mapNotNull { (key, group) ->
                val (stat, operation) = key
                val values = group.map { it.resolve(0.0) }
                val value = when (operation) {
                    EnumModifierOperation.ADD, EnumModifierOperation.INCREASED -> values.sum()
                    // Два MORE - это умножение, а не сложение: 20% и 30% дают 56%, не 50%.
                    EnumModifierOperation.MORE ->
                        (values.fold(1.0) { acc, v -> acc * (1.0 + v / 100.0) } - 1.0) * 100.0
                    // SET не складывается ни с чем: побеждает последний, как в ModifierMath.
                    EnumModifierOperation.SET -> values.last()
                }
                if (value == 0.0) null else StatContribution(stat, operation, value)
            }
            .sortedWith(compareBy({ it.stat.order }, { it.operation.ordinal }))

    /**
     * Разворачивает модификаторы в плоские операции по их описаниям.
     *
     * У составного модификатора эффектов несколько, и значения идут
     * в том же порядке, что и эффекты описания.
     */
    fun expand(modifiers: Collection<Modifier>): List<StatOperation> =
        modifiers.flatMap { modifier ->
            val definition = definitionCache.findById(modifier.modifierId) ?: return@flatMap emptyList()

            definition.effects.mapIndexedNotNull { index, effect ->
                val value = modifier.values.getOrNull(index) ?: return@mapIndexedNotNull null
                StatOperation(
                    stat = effect.stat,
                    operation = effect.operation,
                    value = value,
                    perStat = effect.perStat,
                    perAmount = effect.perAmount
                )
            }
        }

    /**
     * Сворачивает модификаторы одного предмета в операции над персонажем.
     *
     * Локальные считаются внутри предмета от нулевой базы и отдают наружу
     * готовую прибавку: "#% increased Armour" на нагруднике умножает броню
     * этого нагрудника, а не всю броню персонажа. Глобальные проходят как есть.
     */
    fun foldItem(modifiers: Collection<Modifier>): List<StatOperation> {
        val (local, global) = modifiers.partition { isLocal(it) }
        if (local.isEmpty()) return expand(global)

        val folded = compute(emptyMap(), expand(local))
            .filterValues { it != 0.0 }
            .map { (stat, value) -> StatOperation(stat, EnumModifierOperation.ADD, value) }

        return folded + expand(global)
    }

    /**
     * Локальный ли модификатор - то есть считается ли он внутри своего предмета.
     */
    fun isLocal(modifier: Modifier): Boolean =
        definitionCache.findById(modifier.modifierId)?.isLocal == true
}

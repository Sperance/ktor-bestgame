package com.sperance.exileforge.rules.sheet

import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.Line
import com.sperance.exileforge.rules.content.Op
import com.sperance.exileforge.rules.content.SheetStep
import com.sperance.exileforge.rules.content.TakenNode
import com.sperance.exileforge.rules.content.TreeGraph
import com.sperance.exileforge.rules.content.tenths

/** Чем бывает источник характеристики; [StatSource.ref] - код класса, узла, силы, эффекта или id вещи и питомца. */
enum class SourceKind { CLASS, NODE, ITEM, PET, POWER, MAP, ATLAS }

data class StatSource(val kind: SourceKind, val ref: String)

/** Строка с тем, кто её дал. */
data class SourcedLine(val line: Line, val source: StatSource? = null)

/** Строки взятых узлов, каждая со своим узлом. */
fun TreeGraph.sourcedLines(taken: Collection<TakenNode>): List<SourcedLine> = taken.flatMap { t ->
    val node = byCode[t.code] ?: return@flatMap emptyList()
    (t.choice?.let { node.options.getOrNull(it) } ?: node.lines).map { SourcedLine(it, StatSource(SourceKind.NODE, t.code)) }
}

/**
 * Один вклад в характеристику: источник, операция и значение - у конверсии уже от нынешнего [perValue]
 * источника [perStat], по [each] за каждые [per]. [local] - локальные строки вещи, свёрнутые в эту прибавку.
 */
data class Share(
    val source: StatSource?, val op: Op, val value: Double,
    val perStat: String? = null, val perValue: Double = 0.0, val each: Double = 0.0, val per: Double = 1.0,
    val local: List<StatOperation> = emptyList(),
)

/** Сдвиг поверх свода: сила уникалки, эффект карты или Атласа; [from] - чью величину он взял. */
data class Shift(val source: StatSource, val delta: Double, val from: String? = null)

/** Что характеристика даёт другим сейчас. */
data class Grant(val stat: String, val op: Op, val value: Double)

/**
 * Из чего сложилась характеристика: база, вклады по формуле свода и сдвиги поверх.
 * У процент-стата увеличение складывается с плоскими, как в [ModifierMath].
 */
class StatBreakdown(val stat: String, val percent: Boolean, val base: Double, val shares: List<Share>, val shifts: List<Shift>, val total: Double) {
    val flat: List<Share> get() = shares.filter { it.op == Op.ADD || percent && it.op == Op.INCREASED }
    val increased: List<Share> get() = if (percent) emptyList() else shares.filter { it.op == Op.INCREASED }
    val more: List<Share> get() = shares.filter { it.op == Op.MORE }
    val set: Share? get() = shares.lastOrNull { it.op == Op.SET }

    val flatSum: Double get() = tenths(base + flat.sumOf { it.value })
    val increasedSum: Double get() = tenths(increased.sumOf { it.value })
    val moreFactor: Double get() = more.fold(1.0) { acc, it -> acc * (1 + it.value / 100) }
    /** Итог формулы до сдвигов. */
    val formed: Double get() = ModifierMath.apply(base, shares.map { it.op to it.value }, percent)

    fun shifted(extra: List<Shift>): StatBreakdown =
        if (extra.isEmpty()) this else StatBreakdown(stat, percent, base, shares, shifts + extra, tenths(total + extra.sumOf { it.delta }))
}

/**
 * Разбивка листа по источникам - тем же сводом, что [SheetCalculator]: [base] класса и операции с их
 * источниками, конверсии - от величин до сил уникалок, силы - шагами поверх.
 */
class SheetExplainer(private val index: ContentIndex, private val base: Map<String, Double>, private val operations: List<StatOperation>) {
    private val calculator = SheetCalculator(index)
    private val raw: Map<String, Double> by lazy { calculator.raw(base, operations) }
    private val steps: List<SheetStep>
    val stats: Map<String, Double>

    init {
        val traced = mutableListOf<SheetStep>()
        stats = calculator.compute(base, operations) { traced += it }
        steps = traced
    }

    fun explain(stat: String): StatBreakdown {
        val shares = operations.filter { it.stat == stat }.map { op ->
            val from = op.perStat?.let { raw[it] ?: 0.0 } ?: 0.0
            Share(op.source, op.op, op.resolve(from), op.perStat, from, op.value, op.perAmount, op.local)
        }.filter { it.value != 0.0 || it.op == Op.SET }
        val shifts = steps.filter { it.stat == stat && it.delta != 0.0 }.map { Shift(StatSource(SourceKind.POWER, it.power), it.delta, it.from) }
        return StatBreakdown(stat, index.stats.isPercent(stat), base[stat] ?: 0.0, shares, shifts, stats[stat] ?: 0.0)
    }

    fun grants(stat: String): List<Grant> {
        val amount = raw[stat] ?: 0.0
        val converted = operations.filter { it.perStat == stat }.groupBy { it.stat to it.op }
            .map { (key, group) -> Grant(key.first, key.second, tenths(group.sumOf { it.resolve(amount) })) }
        val powered = steps.filter { it.from == stat && it.stat != stat }.map { Grant(it.stat, Op.ADD, it.delta) }
        return (converted + powered).filter { it.value != 0.0 }.sortedBy { index.stats.order(it.stat) }
    }

    /** Где стоит источник силы: операции, что дали её стат. */
    fun holders(power: String): List<StatSource> = operations.filter { it.stat == power }.mapNotNull { it.source }.distinct()
}

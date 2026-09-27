package com.sperance.exileforge.rules.sheet

import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.HeroClass
import com.sperance.exileforge.rules.content.ItemTemplate
import com.sperance.exileforge.rules.content.Line
import com.sperance.exileforge.rules.content.Op
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.content.SheetStep
import com.sperance.exileforge.rules.content.StatRegistry
import com.sperance.exileforge.rules.content.tenths
import com.sperance.exileforge.rules.roll.ItemInstance
import com.sperance.exileforge.rules.roll.Roll
import kotlinx.serialization.Serializable
import kotlin.math.floor

/**
 * Плоская операция над характеристикой: во что разворачивается эффект перед расчётом. [source] - откуда она,
 * [local] - локальные строки вещи, свёрнутые в эту прибавку; расчёту не нужны, их читает разбивка.
 */
data class StatOperation(
    val stat: String, val op: Op, val value: Double, val perStat: String? = null, val perAmount: Double = 1.0,
    val source: StatSource? = null, val local: List<StatOperation> = emptyList(),
) {
    /** У конверсии значение зависит от источника: неполный шаг не засчитывается. */
    fun resolve(source: Double): Double = when {
        perStat == null -> value
        perAmount <= 0.0 -> 0.0
        else -> value * floor(source / perAmount)
    }
}

/** Вклад набора строк по характеристике и виду операции - без базы, поэтому проценты остаются процентами. */
@Serializable
data class StatContribution(val stat: String, val op: Op, val value: Double)

/** Формула свода PoE: `(база + ΣADD) × (1 + ΣINCREASED/100) × Π(1 + MORE/100)`, SET последним; процент-стат складывает INCREASED. */
object ModifierMath {
    fun apply(base: Double, operations: Collection<Pair<Op, Double>>, percent: Boolean = false): Double {
        var result = base + operations.filter { it.first == Op.ADD }.sumOf { it.second }
        val increased = operations.filter { it.first == Op.INCREASED }.sumOf { it.second }
        if (percent) result += increased else result *= (1.0 + increased / 100.0)
        operations.filter { it.first == Op.MORE }.forEach { (_, value) -> result *= (1.0 + value / 100.0) }
        operations.lastOrNull { it.first == Op.SET }?.let { result = it.second }
        return tenths(result)
    }
}

/** Надетая вещь, что не работает: требования не выполнены или гнездо самоцвета не взято. */
@Serializable
data class InactiveItem(val id: String, val code: String, val reasons: List<String>)

/** Лист героя: характеристики, работающие и неработающие вещи, и то, из чего он сложен, - для боя. */
class SheetResult(val stats: Map<String, Double>, val active: List<String>, val inactive: List<InactiveItem>, val base: Map<String, Double>, val operations: List<StatOperation>)

/**
 * Свод модификаторов в характеристики и лист героя - одно правило на сервер и клиент.
 *
 * Класс на уровне героя даёт базу, его строки и дерево - первый проход; вещи - в порядке слотов,
 * каждая проверяется по характеристикам, что дали база, дерево и уже признанные вещи; локальные
 * строки вещи сворачиваются внутри неё. Силы уникалок ложатся поверх готового листа.
 */
class SheetCalculator(private val index: ContentIndex) {
    private val stats: StatRegistry get() = index.stats

    fun expand(lines: Collection<Line>, source: StatSource? = null): List<StatOperation> = lines.flatMap { line ->
        val def = index.modifier(line.code) ?: return@flatMap emptyList()
        def.effects.mapIndexedNotNull { i, effect -> line.values.getOrNull(i)?.let { StatOperation(effect.stat, effect.op, it, effect.perStat, effect.perAmount, source) } }
    }

    fun expand(lines: Collection<SourcedLine>): List<StatOperation> = lines.flatMap { expand(listOf(it.line), it.source) }

    fun expandRolls(rolls: Collection<Roll>, source: StatSource? = null): List<StatOperation> = rolls.flatMap { roll ->
        val def = index.modifier(roll.code) ?: return@flatMap emptyList()
        val values = roll.values(def)
        def.effects.mapIndexedNotNull { i, effect -> values.getOrNull(i)?.let { StatOperation(effect.stat, effect.op, it, effect.perStat, effect.perAmount, source) } }
    }

    /** Свод до сил уникалок: характеристики в порядке реестра, источник конверсии посчитан раньше приёмника. */
    fun raw(base: Map<String, Double>, operations: Collection<StatOperation>): MutableMap<String, Double> {
        val byStat = operations.groupBy { it.stat }
        val result = LinkedHashMap<String, Double>()
        (byStat.keys + base.keys).sortedBy { stats.order(it) }.forEach { stat ->
            val applied = byStat[stat].orEmpty().map { it.op to it.resolve(it.perStat?.let { s -> result[s] } ?: 0.0) }
            result[stat] = ModifierMath.apply(base[stat] ?: 0.0, applied, stats.isPercent(stat))
        }
        return result
    }

    /** Итог по операциям: свод и силы уникалок поверх; [trace] слышит каждый шаг сил. */
    fun compute(base: Map<String, Double>, operations: Collection<StatOperation>, trace: ((SheetStep) -> Unit)? = null): Map<String, Double> =
        index.powers.applySheet(raw(base, operations), trace)

    /** Строки вещи в операции над героем: локальные свёрнуты внутри от нулевой базы и отданы прибавкой. */
    fun foldItem(template: ItemTemplate, rolls: Collection<Roll>, source: StatSource? = null): List<StatOperation> {
        val (local, global) = rolls.partition { index.modifier(it.code)?.local == true }
        val baseOps = expand(template.base, source)
        if (local.isEmpty()) return baseOps + expandRolls(global, source)
        val localOps = expandRolls(local, source)
        val folded = compute(emptyMap(), localOps).filterValues { it != 0.0 }
            .map { (stat, value) -> StatOperation(stat, Op.ADD, value, source = source, local = localOps.filter { it.stat == stat }) }
        return baseOps + folded + expandRolls(global, source)
    }

    /** Что дают строки сами по себе - по строке на характеристику и операцию. */
    fun contributions(operations: Collection<StatOperation>): List<StatContribution> = operations
        .groupBy { it.stat to it.op }
        .mapNotNull { (key, group) ->
            val (stat, op) = key
            val values = group.map { it.resolve(0.0) }
            val value = when (op) {
                Op.ADD, Op.INCREASED -> values.sum()
                Op.MORE -> (values.fold(1.0) { acc, v -> acc * (1.0 + v / 100.0) } - 1.0) * 100.0
                Op.SET -> values.last()
            }
            if (value == 0.0) null else StatContribution(stat, op, value)
        }
        .sortedWith(compareBy({ stats.order(it.stat) }, { it.op.ordinal }))

    /**
     * Лист героя. [lines] - строки взятых узлов и помощников со своими источниками, [equipped] - надетые копии,
     * [takenNodes] - взятые узлы (самоцвет считается, пока взято его гнездо). Инструменты и фляги в лист не входят.
     */
    fun calculate(level: Int, heroClass: HeroClass?, lines: Collection<SourcedLine>, equipped: Collection<ItemInstance>, takenNodes: Set<String>): SheetResult {
        val base = heroClass?.baseOn(level).orEmpty()
        val operations = ArrayList<StatOperation>(expand(heroClass?.lines.orEmpty(), heroClass?.let { StatSource(SourceKind.CLASS, it.code) }))
        operations += expand(lines)
        var stats = compute(base, operations)
        var stale = false
        val active = mutableListOf<String>()
        val inactive = mutableListOf<InactiveItem>()
        equipped.sortedBy { it.slot?.ordinal ?: Int.MAX_VALUE }.forEach { item ->
            val template = index.template(item.template) ?: return@forEach
            if (template.slot.isTool || template.slot.isFlask) return@forEach
            val socket = item.socket
            if (!socket.isNullOrBlank() && socket !in takenNodes) {
                inactive += InactiveItem(item.id, template.code, listOf("socket: need $socket, have none")); return@forEach
            }
            if (template.demanding) {
                if (stale) { stats = compute(base, operations); stale = false }
                val unmet = Requirements.unmet(template, level, stats)
                if (unmet.isNotEmpty()) { inactive += InactiveItem(item.id, template.code, unmet); return@forEach }
            }
            active += item.id
            operations += foldItem(template, item.rolls, StatSource(SourceKind.ITEM, item.id))
            stale = true
        }
        if (stale) stats = compute(base, operations)
        return SheetResult(stats, active, inactive, base, operations)
    }
}

/** Требования вещи - как в PoE, по характеристикам с уже работающей экипировкой; невыполненное не снимает вещь, а выключает. */
object Requirements {
    fun unmet(template: ItemTemplate, level: Int, stats: Map<String, Double>): List<String> = listOfNotNull(
        check("level", template.requiredLevel, level),
        check("strength", template.requiredStrength, (stats["STOCK_STRENGTH"] ?: 0.0).toInt()),
        check("dexterity", template.requiredDexterity, (stats["STOCK_AGILITY"] ?: 0.0).toInt()),
        check("intelligence", template.requiredIntelligence, (stats["STOCK_INTELLECT"] ?: 0.0).toInt()),
    )

    private fun check(name: String, required: Int, actual: Int): String? = if (required <= actual) null else "$name: need $required, have $actual"
}

/** Цена торговца: база шаблона × редкость × доля за каждую строку × `STOCK_GOLD`; никогда не ноль. */
object SellPrice {
    fun of(index: ContentIndex, template: ItemTemplate, rarity: Rarity, rolled: Int, stats: Map<String, Double>): Long {
        val rules = index.rules.sell
        val price = template.basePrice * (rules.rarity[rarity] ?: 1.0) * (1.0 + rules.affixShare * rolled) * (1.0 + (stats["STOCK_GOLD"] ?: 0.0) / 100.0)
        return floor(price).toLong().coerceAtLeast(1L)
    }
}

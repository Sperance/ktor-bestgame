package com.sperance.exileforge.rules.sheet

import com.sperance.exileforge.rules.content.Catalyst
import com.sperance.exileforge.rules.content.Condition
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.HeroClass
import com.sperance.exileforge.rules.content.ItemTemplate
import com.sperance.exileforge.rules.content.Line
import com.sperance.exileforge.rules.content.Op
import com.sperance.exileforge.rules.content.QualityRules
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.content.SheetStep
import com.sperance.exileforge.rules.content.Slot
import com.sperance.exileforge.rules.content.SlotOp
import com.sperance.exileforge.rules.content.SlotPick
import com.sperance.exileforge.rules.content.SlotRule
import com.sperance.exileforge.rules.content.StatRegistry
import com.sperance.exileforge.rules.content.StatSpread
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
    /** Условие боя (1.34.0): такая операция в свод листа не входит - её кладёт бой. */
    val condition: Condition? = null,
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
        def.effects.mapIndexedNotNull { i, effect -> line.values.getOrNull(i)?.let { StatOperation(effect.stat, effect.op, it, effect.perStat, effect.perAmount, source, condition = effect.condition) } }
    }

    fun expand(lines: Collection<SourcedLine>): List<StatOperation> = lines.flatMap { expand(listOf(it.line), it.source) }

    fun expandRolls(rolls: Collection<Roll>, source: StatSource? = null): List<StatOperation> = rolls.flatMap { roll ->
        val def = index.modifier(roll.code) ?: return@flatMap emptyList()
        val values = roll.values(def)
        def.effects.mapIndexedNotNull { i, effect -> values.getOrNull(i)?.let { StatOperation(effect.stat, effect.op, it, effect.perStat, effect.perAmount, source, condition = effect.condition) } }
    }

    /**
     * Операция там, где она считается ([StatSpread]): увеличение урона вообще ([GenericDamage], 1.57.0) - в каждом виде удара,
     * общая характеристика «ко всем X» ([GenericStat], 1.58.0) - в каждом своём члене.
     */
    fun spread(operation: StatOperation): List<StatOperation> =
        StatSpread.targets(operation.stat, operation.op).map { if (it == operation.stat) operation else operation.copy(stat = it) }

    /** Свод до сил уникалок: характеристики в порядке реестра, источник конверсии посчитан раньше приёмника. */
    fun raw(base: Map<String, Double>, operations: Collection<StatOperation>): MutableMap<String, Double> {
        val byStat = operations.filter { it.condition == null }.flatMap(::spread).groupBy { it.stat }
        val result = LinkedHashMap<String, Double>()
        (byStat.keys + base.keys).sortedBy { stats.order(it) }.forEach { stat ->
            val applied = byStat[stat].orEmpty().map { it.op to it.resolve(it.perStat?.let { s -> result[s] } ?: 0.0) }
            result[stat] = ModifierMath.apply(base[stat] ?: 0.0, applied, stats.isPercent(stat))
        }
        return result
    }

    /** Строки труда инструмента [tool] (1.65.0): каждый процент его качества - процент к их значениям. */
    fun toolOperations(tool: com.sperance.exileforge.rules.roll.ItemInstance): List<StatOperation> {
        val share = 1 + tool.quality.coerceAtLeast(0) / 100.0
        return expandRolls(tool.rolls).map { if (share == 1.0) it else it.copy(value = it.value * share) }
    }

    /** Итог по операциям: свод и силы уникалок поверх; [trace] слышит каждый шаг сил. */
    fun compute(base: Map<String, Double>, operations: Collection<StatOperation>, trace: ((SheetStep) -> Unit)? = null): Map<String, Double> =
        index.powers.applySheet(raw(base, operations), trace, stats::isPercent)

    /**
     * Строки вещи в операции над героем. Локальные (1.57.0) сворачиваются внутри вещи поверх её базы, как в PoE:
     * `(база + локальные прибавки) × (1 + локальные увеличения)` - и отдаются одной прибавкой на характеристику.
     */
    fun foldItem(template: ItemTemplate, rolls: Collection<Roll>, source: StatSource? = null, quality: Int = 0, catalyst: Catalyst? = null): List<StatOperation> {
        // Качество (1.35.0): с катализатором - модификаторы его вида, без него - база и локальная защита или физический урон.
        val share = 1 + quality.coerceAtLeast(0) / 100.0
        val boosted: (Roll) -> Boolean = { roll -> catalyst != null && quality > 0 && index.modifier(roll.code)?.let { catalyst.covers(it.tags) } == true }
        fun own(rolls: Collection<Roll>) = rolls.flatMap { roll -> expandRolls(listOf(roll), source).let { ops -> if (boosted(roll)) ops.map { it.copy(value = it.value * share) } else ops } }
        fun based(ops: List<StatOperation>) = if (catalyst != null || quality <= 0) ops
            else ops.map { if (it.stat in QualityRules.BASE_STATS && it.op == Op.ADD) it.copy(value = it.value * share) else it }
        val (local, global) = rolls.partition { index.modifier(it.code)?.local == true }
        val baseOps = expand(template.base, source)
        if (local.isEmpty()) return based(baseOps) + own(global)
        val localOps = own(local)
        val touched = localOps.mapTo(HashSet()) { it.stat }
        // База ложится под локальные строки своей характеристики; её прочие строки и чужие характеристики остаются как есть.
        val (carried, kept) = baseOps.partition { it.stat in touched && it.op == Op.ADD && it.perStat == null && it.condition == null }
        val start = carried.groupBy { it.stat }.mapValues { (_, ops) -> ops.sumOf { it.value } }
        val folded = raw(start, localOps).filterKeys { it in touched }.filterValues { it != 0.0 }
            .map { (stat, value) -> StatOperation(stat, Op.ADD, value, source = source, local = (carried + localOps).filter { it.stat == stat }) }
        return based(kept + folded) + own(global)
    }

    /** Что несёт база вещи с её локальными строками и качеством, по характеристике: подсказка вещи и лист - одним правилом. */
    fun itemBase(template: ItemTemplate, rolls: Collection<Roll>, quality: Int = 0, catalyst: Catalyst? = null): Map<String, Double> =
        raw(emptyMap(), foldItem(template, rolls.filter { index.modifier(it.code)?.local == true }, quality = quality, catalyst = catalyst))

    /** Что дают строки сами по себе - по строке на характеристику и операцию. */
    fun contributions(operations: Collection<StatOperation>): List<StatContribution> = operations
        .filter { it.condition == null }
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
    fun calculate(
        level: Int, heroClass: HeroClass?, lines: Collection<SourcedLine>, equipped: Collection<ItemInstance>, takenNodes: Set<String>,
    ): SheetResult {
        // База крита (1.56.0) - из правил боя: увеличения атак и заклинаний ложатся на свои 5% и 150%, а не на ноль.
        val base = index.campaign.combat.critical.sheetBase + heroClass?.baseOn(level).orEmpty()
        val operations = ArrayList<StatOperation>(expand(heroClass?.lines.orEmpty(), heroClass?.let { StatSource(SourceKind.CLASS, it.code) }))
        operations += expand(lines)
        val own = operations.size
        val worn = mutableListOf<Worn>()
        var stats = compute(base, operations)
        var stale = false
        val active = mutableListOf<String>()
        val inactive = mutableListOf<InactiveItem>()
        val uniqueJewels = HashSet<String>()
        equipped.sortedBy { it.slot?.ordinal ?: Int.MAX_VALUE }.forEach { item ->
            val template = index.template(item.template) ?: return@forEach
            if (template.slot.isTool || template.slot.isFlask) return@forEach
            val socket = item.socket
            if (!socket.isNullOrBlank() && socket !in takenNodes) {
                inactive += InactiveItem(item.id, template.code, listOf("socket: need $socket, have none")); return@forEach
            }
            // Уникальный самоцвет - один такой на героя (1.31.0): второй, вставленный в обход правила, не работает.
            if (template.slot == Slot.JEWEL && template.unique && !uniqueJewels.add(template.code)) {
                inactive += InactiveItem(item.id, template.code, listOf("unique jewel: one ${template.code} per hero")); return@forEach
            }
            if (template.demanding) {
                if (stale) { stats = compute(base, operations); stale = false }
                val unmet = Requirements.unmet(template, level, stats)
                if (unmet.isNotEmpty()) { inactive += InactiveItem(item.id, template.code, unmet); return@forEach }
            }
            active += item.id
            val folded = foldItem(template, item.rolls, StatSource(SourceKind.ITEM, item.id), item.quality, item.catalyst)
            worn += Worn(item, template, folded)
            operations += folded
            stale = true
        }
        // Счёт надетого и силы слотов (1.32.0) ложатся на готовый набор: требования вещей их не видят.
        val counted = base + WornCount.of(equipped, worn.map { it.item to it.template })
        val slotted = slotted(worn)
        if (slotted != null) { operations.subList(own, operations.size).clear(); operations += slotted }
        if (stale || slotted != null || counted.size != base.size) stats = compute(counted, operations)
        return SheetResult(stats, active, inactive, counted, operations)
    }

    /** Работающая вещь героя: копия, шаблон и её операции. */
    private class Worn(val item: ItemInstance, val template: ItemTemplate, val ops: List<StatOperation>)

    /**
     * Силы слотов ([SlotRule], 1.32.0) над работающими вещами: зеркало копирует строки второго кольца в носителя, усиление множит
     * строки выбранных вещей. Доля - ролл силы на носителе, строки самих сил слотов не множатся и не копируются. Null - сил слотов нет.
     */
    private fun slotted(worn: List<Worn>): List<StatOperation>? {
        val rules = index.powers.slotPowers
        if (rules.isEmpty() || worn.none { w -> w.ops.any { it.stat in rules } }) return null
        val factor = DoubleArray(worn.size) { 1.0 }
        val mirrored = Array(worn.size) { emptyList<StatOperation>() }
        worn.forEachIndexed { i, holder ->
            val other = otherRing(holder, worn)
            rules.forEach { (power, rule) ->
                val rolled = holder.ops.filter { it.stat == power && it.op == Op.ADD }.sumOf { it.value }
                if (rolled == 0.0) return@forEach
                if (rule.ifOtherUnique && other?.template?.unique != true) return@forEach
                val value = rule.value ?: rolled
                when (rule.op) {
                    SlotOp.MIRROR -> other?.let { source ->
                        mirrored[i] = mirrored[i] + source.ops.filter { it.stat !in rules }.map { it.scaled(value / 100).copy(source = StatSource(SourceKind.ITEM, holder.item.id)) }
                    }
                    SlotOp.AMPLIFY -> worn.indices.filter { j -> reaches(rule, holder, worn[j], i == j) }.forEach { j -> factor[j] *= (1 + value / 100).coerceAtLeast(0.0) }
                }
            }
        }
        return worn.flatMapIndexed { i, w -> (w.ops + mirrored[i]).map { op -> if (op.stat in rules || factor[i] == 1.0) op else op.scaled(factor[i]) } }
    }

    private fun otherRing(holder: Worn, worn: List<Worn>): Worn? = otherRingPlace(holder.item.slot)?.let { place -> worn.firstOrNull { it.item.slot == place } }

    private fun reaches(rule: SlotRule, holder: Worn, target: Worn, self: Boolean): Boolean {
        if (rule.rarity != null && target.item.rarity != rule.rarity) return false
        return when (rule.pick) {
            SlotPick.SELF -> self
            SlotPick.OTHER_RING -> !self && target.item.slot != null && target.item.slot == otherRingPlace(holder.item.slot)
            SlotPick.SLOTS -> !self && (target.item.slot in rule.slots || target.template.slot in rule.slots)
        }
    }

    private fun otherRingPlace(slot: Slot?): Slot? = when (slot) { Slot.RING -> Slot.RING_2; Slot.RING_2 -> Slot.RING; else -> null }
}

/** Операция, умноженная на [factor]: SET остаётся как есть. */
private fun StatOperation.scaled(factor: Double): StatOperation = if (op == Op.SET) this else copy(value = value * factor)

/**
 * Счёт надетого (1.32.0) - база листа для правил сил: `STOCK_WORN_EMPTY_SLOTS` - пустые места тела (9 мест, основная рука и вторая:
 * двуручное занимает обе), `STOCK_WORN_UNIQUES`, `STOCK_WORN_CORRUPTED`, `STOCK_WORN_JEWELS` - работающие уникалки, осквернённые вещи
 * и самоцветы. Ноль в базу не пишется.
 */
object WornCount {
    const val EMPTY_SLOTS = "STOCK_WORN_EMPTY_SLOTS"
    const val UNIQUES = "STOCK_WORN_UNIQUES"
    const val CORRUPTED = "STOCK_WORN_CORRUPTED"
    const val JEWELS = "STOCK_WORN_JEWELS"
    val STATS = listOf(EMPTY_SLOTS, UNIQUES, CORRUPTED, JEWELS)
    private val BODY = listOf(Slot.HELMET, Slot.BODY, Slot.GLOVES, Slot.BOOTS, Slot.WINGS, Slot.BELT, Slot.AMULET, Slot.RING, Slot.RING_2)

    /** Счёт по надетому [equipped] (места тела) и работающим вещам [active] с их шаблонами. */
    fun of(equipped: Collection<ItemInstance>, active: List<Pair<ItemInstance, ItemTemplate>>): Map<String, Double> {
        val taken = equipped.filter { !it.socketed }.mapNotNullTo(HashSet()) { it.slot }
        val mainHand = Slot.WEAPON_1H in taken || Slot.WEAPON_2H in taken
        val offHand = Slot.WEAPON_2H in taken || Slot.SHIELD in taken || Slot.QUIVER in taken
        val empty = BODY.count { it !in taken } + (if (mainHand) 0 else 1) + (if (offHand) 0 else 1)
        return mapOf(
            EMPTY_SLOTS to empty.toDouble(),
            UNIQUES to active.count { (_, template) -> template.unique }.toDouble(),
            CORRUPTED to active.count { (item, _) -> item.corrupted }.toDouble(),
            JEWELS to active.count { (_, template) -> template.slot == Slot.JEWEL }.toDouble(),
        ).filterValues { it != 0.0 }
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

/**
 * Цена торговца (1.22.0), одна для продажи и витрины (витрина - она же × наценка): своя цена шаблона или база,
 * растущая с его уровнем как золото с монстров, × редкость × доля за каждую строку × качество роллов × `STOCK_GOLD`;
 * никогда не ноль.
 */
object SellPrice {
    fun of(index: ContentIndex, template: ItemTemplate, item: ItemInstance, stats: Map<String, Double> = emptyMap()): Long {
        val rules = index.rules.sell
        val price = base(index, template, if (template.slot == Slot.MAP) item.level(template) else template.level) * (rules.rarity[item.rarity] ?: 1.0) * (1.0 + rules.affixShare * item.rolls.size) *
            (rules.qualityFloor + quality(item.rolls, rules.neutralQuality)) * goldBonus(index, stats)
        val sell = floor(price).toLong().coerceAtLeast(1L)
        return if (item.resale > 0) sell.coerceAtMost(item.resale) else sell
    }

    /**
     * Множитель `STOCK_GOLD` (1.53.0) не выше [RESALE_SHARE] наценки торговца: витрина стоит базу × наценку, и продать ему
     * купленное дороже покупки нельзя ни с каким листом - купить и сразу продать всегда в убыток.
     */
    fun goldBonus(index: ContentIndex, stats: Map<String, Double>): Double =
        (1.0 + (stats["STOCK_GOLD"] ?: 0.0) / 100.0).coerceAtMost(index.rules.merchant.markup * RESALE_SHARE)

    /** Какая доля цены витрины - потолок продажи торговцу. */
    const val RESALE_SHARE = 0.9

    /** Потолок продажи вещи, купленной у торговца за [price] ([ItemInstance.resale]). */
    fun resaleCap(price: Long): Long = floor(price * RESALE_SHARE).toLong().coerceAtLeast(1L)

    /** База шаблона: своя цена или [SellRules.base] × рост золота до [level] - уровня шаблона, у карты - уровня её зоны. */
    fun base(index: ContentIndex, template: ItemTemplate, level: Int = template.level): Double =
        template.price?.toDouble() ?: (index.rules.sell.base * index.rules.loot.goldScale(level, index.campaign.growthTaper))

    /** Качество роллов 0..1: средняя доля строк; без строк - [neutral]. */
    fun quality(rolls: List<Roll>, neutral: Double): Double = if (rolls.isEmpty()) neutral else rolls.sumOf { it.share.coerceIn(0.0, 1.0) } / rolls.size
}

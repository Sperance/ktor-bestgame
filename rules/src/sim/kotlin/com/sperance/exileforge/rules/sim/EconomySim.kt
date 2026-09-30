package com.sperance.exileforge.rules.sim

import com.sperance.exileforge.rules.content.AutoSell
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.ContentLoader
import com.sperance.exileforge.rules.content.HeroClass
import com.sperance.exileforge.rules.content.Item
import com.sperance.exileforge.rules.content.ItemTemplate
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.content.Slot
import com.sperance.exileforge.rules.content.SlotGroup
import com.sperance.exileforge.rules.content.WeaponType
import com.sperance.exileforge.rules.content.Zone
import com.sperance.exileforge.rules.roll.Dice
import com.sperance.exileforge.rules.roll.ItemFactory
import com.sperance.exileforge.rules.roll.ItemInstance
import com.sperance.exileforge.rules.roll.MonsterRoller
import com.sperance.exileforge.rules.roll.RolledMonster
import com.sperance.exileforge.rules.roll.Streams
import com.sperance.exileforge.rules.run.Reward
import com.sperance.exileforge.rules.run.RewardDraws
import com.sperance.exileforge.rules.run.Run
import com.sperance.exileforge.rules.run.RunContext
import com.sperance.exileforge.rules.sheet.CombatProfile
import com.sperance.exileforge.rules.sheet.Requirements
import com.sperance.exileforge.rules.sheet.SellPrice
import com.sperance.exileforge.rules.sheet.SheetCalculator
import java.io.File
import java.util.Locale

/** Строка отчёта: архетип (класс) на уровне; всё «в час» - на чистое время боя захода. */
data class EconomyRow(
    val heroClass: String, val level: Int, val zone: String, val zoneLevel: Int, val dps: Double,
    val ttk: Double, val runSeconds: Double, val xpPerHour: Double, val goldPerHour: Double, val orbsPerHour: Double,
    val itemsPerHour: Double, val autoSellPerHour: Double, val runGold: Double, val worstItem: String, val worstPrice: Long,
) {
    /** Одна вещь дороже [OUTLIER]× золота захода - выброс экономики. */
    val outlier: Boolean get() = worstPrice > OUTLIER * runGold

    companion object { const val OUTLIER = 10.0 }
}

/** Проверка арбитража торговца на уровне: худший случай «купил обычную - сделал лучшую редкую - продал». */
data class Arbitrage(val level: Int, val template: String, val price: Long, val crafted: Long, val capped: Long) {
    val broken: Boolean get() = capped > price
}

/**
 * Симуляция экономики по правилам `rules`: каждый класс на уровнях 1..max в редкой экипировке своего уровня со средними
 * роллами и пустым деревом проходит лучшую зону уровня на [seeds] семенах - все стаи, сундуки и босс. Урон - сильнейший
 * [CombatProfile], время стаи - как у сервера (сильнейший монстр: здоровье и щит на урон). Детерминирована семенами.
 */
class EconomySim(private val index: ContentIndex, private val seeds: Int = 20) {
    private val factory = ItemFactory(index)
    private val calculator = SheetCalculator(index)
    private val roller = MonsterRoller(index)
    private val sellAll = AutoSell(Rarity.entries.associateWith { SlotGroup.entries.toSet() })
    private val worstCache = HashMap<Int, Pair<String, Long>>()

    val levels: List<Int> get() = (listOf(1) + (STEP..index.classes.maxLevel step STEP)).distinct()

    /** Архетипы - классы с общей базой ([ContentIndex.heroClass]), как их видит лист героя. */
    fun rows(): List<EconomyRow> = index.classes.classes.mapNotNull { index.heroClass(it.code) }.flatMap { heroClass -> levels.map { simulate(heroClass, it) } }

    fun simulate(heroClass: HeroClass, level: Int): EconomyRow {
        val equipped = gear(heroClass, level)
        val stats = calculator.calculate(level, heroClass, emptyList(), equipped, emptySet()).stats
        val dps = CombatProfile.of(index, stats).best
        val zone = zoneFor(level)
        var reward = Reward.NONE
        var seconds = 0.0
        var packs = 0
        repeat(seeds) { s ->
            val seed = SEED + s
            val run = Run(index, zone, seed, RunContext(heroClass.code, level))
            val draws = RewardDraws(Streams.mix(seed, level.toLong(), heroClass.code.hashCode().toLong()), 0)
            repeat(run.count) { i ->
                val pack = run.spawn(i).pack
                seconds += packSeconds(pack, dps)
                packs++
                pack.indices.forEach { m -> run.kill(i, m, false, draws)?.let { reward += it } }
            }
            repeat(Dice(seed).between(index.campaign.chests.count)) { reward += run.chest(draws) }
            if (zone.boss.isNotBlank()) {
                seconds += packSeconds(listOf(roller.boss(roller.guardian(zone.boss, zone.level), Dice(seed))), dps)
                reward += run.boss(draws)
            }
        }
        val hours = (seconds / 3600).coerceAtLeast(1e-9)
        val orbs = reward.items.filterKeys { index.item(it)?.category == Item.CURRENCY }.values.sum()
        val sold = reward.equipment.sumOf { item -> index.template(item.template)?.takeIf { sellAll.sells(it, item) }?.let { SellPrice.of(index, it, item, stats) } ?: 0L }
        val (worst, worstPrice) = worstCache.getOrPut(zone.level) { worstItem(zone.level) }
        return EconomyRow(
            heroClass.code, level, zone.code, zone.level, dps, seconds / packs.coerceAtLeast(1), seconds / seeds,
            reward.experience / hours, reward.gold / hours, orbs / hours, reward.equipment.size / hours, sold / hours,
            reward.gold.toDouble() / seeds, worst, worstPrice,
        )
    }

    /** Лучшая зона уровня: высший уровень зоны не выше героя; ниже первой зоны - первая. */
    fun zoneFor(level: Int): Zone = index.zones.values.filter { it.level <= level }.maxWithOrNull(compareBy({ it.level }, { it.code }))
        ?: index.zones.values.minBy { it.level }

    /** Экипировка класса: оружие рода стартового, к нему колчан (лук) или щит (одноручное), броня и бижутерия - редкие, роллы средние. */
    fun gear(heroClass: HeroClass, level: Int): List<ItemInstance> {
        val own = calculator.calculate(level, heroClass, emptyList(), emptyList(), emptySet()).stats
        val wearable = index.templates.values.filter { !it.unique && !it.corrupted && it.tables.isNotEmpty() && Requirements.unmet(it, level, own).isEmpty() }
        fun best(slot: Slot, fits: (ItemTemplate) -> Boolean = { true }) =
            wearable.filter { it.slot == slot && fits(it) }.maxWithOrNull(compareBy({ it.requiredLevel }, { it.level }, { it.code }))
        val start = index.template(heroClass.weapon)
        val weapon = start?.let { best(it.slot) { t -> t.weaponType == it.weaponType } }
        val offhand = when {
            start?.weaponType == WeaponType.BOW -> Slot.QUIVER
            start?.slot == Slot.WEAPON_1H -> Slot.SHIELD
            else -> null
        }
        val places = listOfNotNull(weapon?.let { it to it.slot }, offhand?.let { slot -> best(slot)?.let { it to slot } }) +
            ARMOUR.mapNotNull { slot -> best(slot)?.let { it to slot } } +
            listOfNotNull(best(Slot.RING)?.let { it to Slot.RING_2 })
        val dice = Dice(Streams.mix(SEED, level.toLong(), heroClass.code.hashCode().toLong()))
        return places.mapIndexed { n, (template, slot) ->
            val item = factory.create("g$n", template, Rarity.RARE, dice, level = index.rules.loot.itemLevel(level))
            item.copy(rolls = item.rolls.map { it.copy(share = AVERAGE) }, slot = slot)
        }
    }

    /** Самая дорогая вещь, какая может выпасть в зоне уровня [level]: высшая редкость шаблона, все места строк, лучшие роллы, предельный `STOCK_GOLD`. */
    fun worstItem(level: Int): Pair<String, Long> {
        val reach = index.rules.loot.uniqueReach
        return index.templates.values.filter { !it.slot.isTool && it.slot != Slot.MAP && it.requiredLevel <= level + if (it.unique) reach else 0 }
            .map { template -> template.code to SellPrice.of(index, template, best(template, level), MAX_GOLD) }
            .maxWithOrNull(compareBy({ it.second }, { it.first })) ?: ("-" to 0L)
    }

    /** Лучшая копия шаблона: уникалка - своя редкость, прочее - редкая (фляга - волшебная) до потолка строк, роллы 1.0. */
    private fun best(template: ItemTemplate, level: Int): ItemInstance {
        val rarity = if (template.unique) template.rarity else Rarity.RARE
        val item = factory.create("w", template, rarity, Dice(SEED), level = level)
        val rolls = item.rolls.map { it.copy(share = 1.0) }
        val affixes = rolls.filter { index.modifier(it.code)?.affix == true }
        val missing = if (item.rarity.fixed || affixes.isEmpty()) 0 else (index.limits(item.rarity, template.slot).ceiling - affixes.size).coerceAtLeast(0)
        return item.copy(rolls = rolls + List(missing) { affixes.first() })
    }

    /** Арбитраж на уровне [level]: витрина торговца - обычная копия × наценка; после лучшего ремесла продажа упирается в [ItemInstance.resale]. */
    fun arbitrage(level: Int): Arbitrage? {
        val rules = index.rules.merchant
        val gear = index.forHero(index.templatePool(rules.tables), level).map { it.value }.filter { !it.slot.isFlask && it.requiredLevel <= level + rules.levelSpread }
        return gear.map { template ->
            val bought = factory.create("m", template, Rarity.COMMON, Dice(SEED), level = index.rules.loot.itemLevel(level))
            val price = SellPrice.of(index, template, bought) * rules.markup
            val crafted = best(template, index.rules.loot.itemLevel(level))
            Arbitrage(level, template.code, price, SellPrice.of(index, template, crafted, MAX_GOLD),
                SellPrice.of(index, template, crafted.copy(resale = SellPrice.resaleCap(price)), MAX_GOLD))
        }.maxWithOrNull(compareBy({ it.capped.toDouble() / it.price }, { it.crafted.toDouble() / it.price }))
    }

    companion object {
        const val STEP = 5
        const val SEED = 20_260_930L
        const val AVERAGE = 0.5
        val ARMOUR = listOf(Slot.HELMET, Slot.BODY, Slot.GLOVES, Slot.BOOTS, Slot.WINGS, Slot.BELT, Slot.AMULET, Slot.RING)
        /** `STOCK_GOLD` выше любого потолка: [SellPrice.goldBonus] сам срежет его до предела. */
        val MAX_GOLD = mapOf("STOCK_GOLD" to 1e6)

        /** Секунды боя со стаей - как `Plausibility.packSeconds` сервера. */
        fun packSeconds(pack: List<RolledMonster>, dps: Double): Double =
            (pack.maxOfOrNull { (it.stats["STOCK_HEALTH"] ?: 0.0) + (it.stats["STOCK_ENERGY_SHIELD"] ?: 0.0) } ?: 0.0) / dps
    }
}

/** Отчёт: markdown для чтения и CSV для таблиц. */
object EconomyReport {
    private val header = listOf("class", "level", "zone", "zoneLevel", "dps", "ttkSec", "runSec", "xpH", "goldH", "orbsH", "itemsH", "autoSellH", "runGold", "worstItem", "worstPrice", "outlier")

    private fun f(value: Double) = String.format(Locale.ROOT, "%.1f", value)

    private fun cells(row: EconomyRow) = with(row) {
        listOf(heroClass, "$level", zone, "$zoneLevel", f(dps), f(ttk), f(runSeconds), f(xpPerHour), f(goldPerHour), f(orbsPerHour),
            f(itemsPerHour), f(autoSellPerHour), f(runGold), worstItem, "$worstPrice", if (outlier) "YES" else "")
    }

    fun csv(rows: List<EconomyRow>): String = (listOf(header) + rows.map(::cells)).joinToString("\n", postfix = "\n") { it.joinToString(",") }

    fun markdown(rows: List<EconomyRow>, arbitrage: List<Arbitrage>, seeds: Int): String = buildString {
        appendLine("# Economy simulation")
        appendLine()
        appendLine("Seeds per row: $seeds. Gear: best RARE per slot for the level, average rolls (0.5), empty tree. Per-hour values use pure fight time.")
        appendLine("Outlier: the most expensive possible item of the zone sells for more than ${EconomyRow.OUTLIER.toInt()}x the average run gold.")
        appendLine()
        appendLine(header.joinToString(" | ", "| ", " |"))
        appendLine(header.joinToString(" | ", "| ", " |") { "---" })
        rows.forEach { appendLine(cells(it).joinToString(" | ", "| ", " |")) }
        appendLine()
        appendLine("Outliers: ${rows.count { it.outlier }} of ${rows.size}")
        appendLine()
        appendLine("## Merchant arbitrage (bought COMMON, crafted to best RARE, max gold bonus)")
        appendLine()
        appendLine("| level | template | price | crafted (uncapped) | sold (resale cap) | broken |")
        appendLine("| --- | --- | --- | --- | --- | --- |")
        arbitrage.forEach { appendLine("| ${it.level} | ${it.template} | ${it.price} | ${it.crafted} | ${it.capped} | ${if (it.broken) "YES" else ""} |") }
        appendLine()
        appendLine("Broken levels: ${arbitrage.count { it.broken }}")
    }
}

fun main(args: Array<String>) {
    val content = File(System.getProperty("content") ?: "../src/main/resources/content")
    val out = File(args.firstOrNull() ?: "build/reports/economy").apply { mkdirs() }
    val seeds = System.getProperty("seeds")?.toIntOrNull() ?: 20
    val index = ContentLoader.load { File(content, it).readText() }
    val sim = EconomySim(index, seeds)
    val rows = sim.rows()
    val arbitrage = sim.levels.mapNotNull(sim::arbitrage)
    File(out, "economy.csv").writeText(EconomyReport.csv(rows))
    File(out, "economy.md").writeText(EconomyReport.markdown(rows, arbitrage, seeds))
    println("Economy report: ${File(out, "economy.md").absolutePath} (${rows.count { it.outlier }} outliers, ${arbitrage.count { it.broken }} broken arbitrage levels)")
}

package com.sperance.exileforge.rules

import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.ContentLoader
import com.sperance.exileforge.rules.content.Orb
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.roll.Dice
import com.sperance.exileforge.rules.roll.ItemFactory
import com.sperance.exileforge.rules.roll.ItemInstance
import com.sperance.exileforge.rules.roll.Menagerie
import com.sperance.exileforge.rules.content.PetOrbAction
import com.sperance.exileforge.rules.roll.OrbApplier
import com.sperance.exileforge.rules.roll.Veils
import com.sperance.exileforge.rules.run.Run
import com.sperance.exileforge.rules.run.RunContext
import com.sperance.exileforge.rules.run.RewardDraws
import com.sperance.exileforge.rules.content.Slot
import com.sperance.exileforge.rules.content.Source
import com.sperance.exileforge.rules.table.TableKind
import com.sperance.exileforge.rules.table.Weighted
import com.sperance.exileforge.rules.content.VariantKind
import com.sperance.exileforge.rules.content.SkillNodeType
import com.sperance.exileforge.rules.content.TreeAllocation
import com.sperance.exileforge.rules.content.GenericDamage
import com.sperance.exileforge.rules.content.GenericStat
import com.sperance.exileforge.rules.content.Op
import com.sperance.exileforge.rules.content.tenths
import com.sperance.exileforge.rules.roll.Roll
import com.sperance.exileforge.rules.sheet.SheetCalculator
import com.sperance.exileforge.rules.sheet.StatOperation
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Правила, которые нельзя нарушить ни одним путём: волшебная и редкая копия не пуста и не переполнена - ни из
 * фабрики, ни после любой сферы и их цепочки от обычной; заход не выдаёт одну и ту же награду дважды, а награда
 * определяется только потоком наград сервера.
 */
class RulesInvariantsTest {
    private val index: ContentIndex by lazy {
        val resources = File(System.getProperty("content.resources") ?: "../src/main/resources")
        ContentLoader.load { File(resources, "content/$it").readText() }
    }

    private fun affixes(item: ItemInstance) = item.rolls.count { index.modifier(it.code)?.affix == true }

    /** Дно и потолок редкости: волшебная и редкая - от дна до потолка, в пределах мест префиксов и суффиксов. */
    private fun floorHeld(item: ItemInstance): Boolean {
        val template = index.template(item.template) ?: return true
        if (item.rarity.fixed) return true
        val limits = index.limits(item.rarity, template.slot)
        val sources = item.rolls.mapNotNull { index.modifier(it.code) }.filter { it.affix }.groupingBy { it.source }.eachCount()
        val floor = if (item.rarity == Rarity.MAGIC || item.rarity == Rarity.RARE) limits.floor else 0
        return affixes(item) in floor..limits.ceiling && (sources[Source.PREFIX] ?: 0) <= limits.prefixes && (sources[Source.SUFFIX] ?: 0) <= limits.suffixes
    }

    @Test
    fun magicAndRareAreNeverEmptyAfterAnyOrb() {
        val factory = ItemFactory(index)
        val orbs = OrbApplier(index)
        val templates = index.templates.values.filter { !it.unique && it.tables.isNotEmpty() }.distinctBy { it.slot }
        var n = 0
        templates.forEach { template ->
            listOf(Rarity.MAGIC, Rarity.RARE).forEach { rarity ->
                val item = factory.create("i", template, rarity, Dice(7L + n))
                assertTrue(floorHeld(item), "${template.code} ${item.rarity} from the factory: ${affixes(item)} affixes")
                Orb.entries.forEach { orb ->
                    // Отказ правила (сфера не для этой вещи) - не нарушение: вещь осталась, какой была
                    val outcome = runCatching { orbs.apply(orb, item.copy(rolls = item.rolls.toList()), template, Dice(100L + n++)) { "new" } }.getOrNull() ?: return@forEach
                    assertTrue(floorHeld(outcome.item), "${template.code} $rarity after $orb: ${outcome.item.rarity} ${affixes(outcome.item)} affixes")
                    outcome.created?.let { assertTrue(floorHeld(it), "${template.code} copy of $orb") }
                }
            }
        }
    }

    /**
     * Пул аффиксов гейтится уровнем вещи (1.61.0): ни ролл, ни сфера не ставят аффикс, чей худший тир выше уровня вещи, и
     * волшебная и редкая вещь низкого уровня всё равно добирает дно своей редкости.
     */
    @Test
    fun noAffixRollsAboveTheItemLevelAndLowLevelItemsKeepTheirFloor() {
        val factory = ItemFactory(index)
        val orbs = OrbApplier(index)
        val templates = index.templates.values.filter { !it.unique && it.tables.isNotEmpty() }.distinctBy { it.tables }
        fun check(item: ItemInstance, level: Int, what: String) {
            assertTrue(floorHeld(item), "$what: ${item.rarity} ${affixes(item)} affixes at ilvl $level")
            val above = item.rolls.mapNotNull { index.modifier(it.code) }.filter { it.affix && !it.openAt(level) }
            assertTrue(above.isEmpty(), "$what: ${above.map { it.code to it.minLevel }} on ilvl $level")
        }
        var n = 0L
        templates.forEach { template ->
            listOf(1, 5, 10).forEach { level ->
                listOf(Rarity.MAGIC, Rarity.RARE).forEach { rarity ->
                    repeat(3) {
                        val item = factory.create("i", template, rarity, Dice(9_000L + n++), level = level)
                        check(item, level, "${template.code} $rarity from the factory")
                        Orb.entries.forEach { orb ->
                            val outcome = runCatching { orbs.apply(orb, item.copy(rolls = item.rolls.toList()), template, Dice(n++)) { "new" } }.getOrNull() ?: return@forEach
                            check(outcome.item, level, "${template.code} $rarity after $orb")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun aChainOfOrbsFromCommonHoldsFloorAndCeiling() {
        val factory = ItemFactory(index)
        val orbs = OrbApplier(index)
        val templates = index.templates.values.filter { !it.unique && it.tables.isNotEmpty() && factory.rarityFor(it, Rarity.COMMON) == Rarity.COMMON }.distinctBy { it.slot }
        val starts = listOf(Orb.ORB_OF_TRANSMUTATION, Orb.ORB_OF_ALCHEMY, Orb.ORB_OF_CHANCE)
        var n = 0L
        templates.forEach { template ->
            starts.forEach { start ->
                val dice = Dice(500L + n++)
                var item = factory.create("i", template, Rarity.COMMON, dice)
                (listOf(start) + List(6) { Orb.entries[dice.nextInt(Orb.entries.size)] }).forEach { orb ->
                    if (orb == Orb.MIRROR_OF_KALANDRA) return@forEach
                    item = runCatching { orbs.apply(orb, item.copy(rolls = item.rolls.toList()), template, dice) { "new" }.item }.getOrNull() ?: item
                    assertTrue(floorHeld(item), "${template.code} after $start..$orb: ${item.rarity} ${affixes(item)} affixes")
                }
            }
        }
    }

    @Test
    fun aPetKeepsItsRaritysLinesThroughAnyOrb() {
        val pets = Menagerie(index)
        val dice = Dice(7L)
        index.pets.eggs.values.forEach { egg ->
            repeat(40) { n ->
                var pet = pets.hatch(egg, "p$n", dice)!!
                repeat(30) {
                    pet = pets.apply(PetOrbAction.entries[dice.nextInt(PetOrbAction.entries.size)], pet, dice) ?: pet
                    val rule = index.pets.rarities.getValue(pet.rarity)
                    assertTrue(pet.lines.size in rule.floor..rule.ceiling, "${pet.species} ${pet.rarity}: ${pet.lines.size} lines")
                    assertEquals(pet.lines.size, pet.lines.map { it.code }.toSet().size, "a line twice on ${pet.species}")
                    assertEquals(pet.lines.size, pets.lines(pet).size, "a line out of the pool of ${pet.species}")
                }
            }
        }
    }

    @Test
    fun alchemyRollsFromTheFloorToOneBelowTheCeiling() {
        val template = index.templates.values.first { it.rarity == Rarity.COMMON && it.tables.isNotEmpty() && !it.slot.isJewelLike }
        val limits = index.limits(Rarity.RARE, template.slot)
        val counts = (1L..200L).map { seed ->
            val item = ItemInstance("i", template.code, Rarity.COMMON)
            affixes(OrbApplier(index).apply(Orb.ORB_OF_ALCHEMY, item, template, Dice(seed)) { "new" }.item)
        }.toSet()
        assertEquals((limits.floor until limits.ceiling).toSet(), counts)
    }

    @Test
    fun aRunNeverPaysTheSameRewardTwice() {
        val zone = index.zones.values.first { it.boss.isNotBlank() && it.chestLoot.isNotBlank() }
        val context = RunContext("", zone.level)
        val run = Run(index, zone, 99L, context)
        val draws = RewardDraws(7L, 0)
        val chests = List(3) { run.chest(draws) }
        val ids = chests.flatMap { reward -> reward.equipment.map { it.id } } + List(2) { run.boss(draws) }.flatMap { reward -> reward.equipment.map { it.id } }
        assertEquals(ids.size, ids.toSet().size, "item ids repeat: $ids")
        assertEquals(5L, draws.drawn)
        // Награду решает поток наград, а не семя захода: тот же номер потока - та же награда и на заходе с другим семенем
        val stream = RewardDraws(7L, 5)
        val next = run.chest(draws)
        val other = Run(index, zone, 12345L, context).chest(stream)
        assertEquals(next.items, other.items)
        assertEquals(next.equipment.map { it.template to it.rolls }, other.equipment.map { it.template to it.rolls })
        // Отклонённое убийство (нет такого жетона) номер не тянет
        assertEquals(null, run.kill(run.count, 0, false, draws))
        assertEquals(6L, draws.drawn)
    }

    /** Самоцвет не выпадает герою ниже `loot.jewelHeroLevel` ни из пула, ни из наград захода; с него - выпадает. */
    @Test
    fun jewelsWaitForTheirHeroLevel() {
        val gate = index.rules.loot.jewelHeroLevel
        val all = index.templates.values.map { Weighted(it, 1) }
        assertTrue(index.forHero(all, gate - 1).none { it.value.slot == Slot.JEWEL })
        assertTrue(index.forHero(all, gate).any { it.value.slot == Slot.JEWEL })
        val zone = index.zones.values.filter { it.boss.isNotBlank() && it.chestLoot.isNotBlank() }.maxBy { it.level }
        val run = Run(index, zone, 99L, RunContext("", gate - 1))
        val draws = RewardDraws(7L, 0)
        val dropped = (List(200) { run.chest(draws) } + List(50) { run.boss(draws) }).flatMap { it.equipment }
        assertTrue(dropped.none { index.template(it.template)?.slot == Slot.JEWEL }, "a jewel below hero level $gate")
    }

    /** Обычные аффиксы предметов (префиксы и суффиксы, кроме строк карт) - без минусов: ни одна строка не вредит герою. */
    @Test
    fun ordinaryAffixesCarryNoDebuff() {
        val debuffs = index.definitions.filter { it.source.affix && it.effects.none { e -> e.stat.startsWith("MAP_") } }.flatMap { def ->
            def.effects.withIndex().filter { (i, effect) ->
                val values = def.tiers.flatMap { it.values.getOrNull(i).orEmpty() }
                if (effect.stat.endsWith("_TAKEN")) values.any { it > 0 } else values.any { it < 0 }
            }.map { (_, effect) -> "${def.code}: ${effect.stat}" }
        }
        assertTrue(debuffs.isEmpty(), "affix debuffs: $debuffs")
    }

    /**
     * Обычный аффикс предмета и самоцвета (1.58.0) даёт ровно одно: гибриды ушли, «ко всем X» - один общий стат ([GenericStat]),
     * который лист раскладывает по членам. Строки влияния, завесы и инструментов профессий - свои пулы, их правило не трогает.
     */
    @Test
    fun ordinaryAffixesGiveOneThing() {
        val hybrids = index.definitions.filter { it.source.affix && it.influence == null && "veiled" !in it.tags && "work" !in it.tags && it.effects.size > 1 }
        assertTrue(hybrids.isEmpty(), "hybrid affixes: ${hybrids.map { it.code }}")
        val calc = SheetCalculator(index)
        GenericStat.entries.forEach { generic ->
            val sheet = calc.raw(emptyMap(), listOf(StatOperation(generic.code, Op.ADD, 7.0)))
            generic.members.forEach { assertEquals(7.0, sheet[it], "${generic.code} -> $it") }
            assertEquals(null, sheet[generic.code])
        }
    }

    /**
     * Суффикс или префикс, ушедший из всех пулов слотов, не пропадает из игры: его семейство ставит верстак
     * (CRAFTED-вариант) или порча (осквернённый вариант в таблице `corruption:*`).
     */
    @Test
    fun everyAffixFamilyStaysObtainable() {
        val (corruption, natural) = index.tables.tags.filter { index.tables.kind(it) == TableKind.MODIFIER }
            .partition { it.startsWith(CORRUPTION_TABLES) }
        fun families(tags: List<String>) = tags.flatMap { tag -> index.tables.members(tag).values.filter { it.weight > 0 }.mapNotNull { index.modifier(it.code) } }
        val obtainable = families(natural).map { it.family }.toSet() +
            families(corruption).filter { it.variant == VariantKind.CORRUPTED }.map { it.family } +
            index.definitions.filter { it.crafted }.map { it.family }
        val lost = index.definitions.filter { it.affix && it.variant == VariantKind.NATURAL && it.rolls && !it.veiled && it.family !in obtainable }
        assertTrue(lost.isEmpty(), "affix families nowhere to get: ${lost.map { it.code }}")
    }

    /** Путь к дальнему узлу (1.37.0): кратчайший, от взятого, узел с выбором - только целью, и каждый его шаг - законное взятие. */
    @Test
    fun treePathIsShortestAndLegal() {
        val tree = index.tree
        val start = tree.byCode.values.first { it.type == SkillNodeType.START && it.code != "SCION_START" }.code
        val target = tree.byCode.values.filter { it.type == SkillNodeType.NOTABLE && it.openTo(start) }
            .mapNotNull { n -> TreeAllocation.path(tree, listOf(start), start, n.code)?.let { n to it } }
            .maxBy { it.second.size }
        val taken = mutableListOf(start)
        target.second.forEach { code ->
            val node = tree.node(code)!!
            TreeAllocation.requireAllocatable(tree, node, taken, start, 999, if (node.options.isEmpty()) null else 0)
            assertTrue(code == target.first.code || node.options.isEmpty(), "узел с выбором в середине пути: $code")
            taken += code
        }
        assertEquals(target.first.code, target.second.last())
        assertEquals(null, TreeAllocation.path(tree, taken, start, target.first.code))
    }

    /** Гнёзда самоцветов поровну (1.39.0): каждое гнездо, не закреплённое за классом, достижимо с каждого старта. */
    @Test
    fun everySocketIsOpenToEveryClass() {
        val tree = index.tree
        val starts = tree.byCode.values.filter { it.type == SkillNodeType.START }.map { it.code }
        fun reach(start: String): Set<String> {
            val seen = hashSetOf(start); val queue = ArrayDeque(listOf(start))
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                if (tree.node(current)?.type == SkillNodeType.MASTERY) continue
                tree.neighbours(current).filter { next -> tree.node(next)?.let { it.type != SkillNodeType.START && it.openTo(start) } == true && seen.add(next) }
                    .forEach(queue::addLast)
            }
            return seen
        }
        val open = starts.associateWith(::reach)
        val closed = tree.byCode.values.filter { it.type == SkillNodeType.JEWEL_SOCKET && it.only == null }
            .flatMap { socket -> starts.filter { socket.code !in open.getValue(it) }.map { "${socket.code} от $it" } }
        assertTrue(closed.isEmpty(), "гнездо закрыто: $closed")
    }

    /**
     * Локальные строки (1.57.0) растят базу своей вещи, как в PoE, - одинаково в листе и в подсказке; увеличение урона
     * вообще складывается с увеличениями вида удара, а урон крита по базе 100 не трогает множитель.
     */
    @Test
    fun localLinesGrowTheItemsOwnBase() {
        val calc = SheetCalculator(index)
        val sword = index.template("RUSTED_SWORD")!!
        val increased = Roll("INCREASED_PHYSICAL_DAMAGE@LOCAL", tier = 1, share = 1.0)
        val flat = Roll("ADD_PHYSICAL_DAMAGE@LOCAL", tier = 1, share = 1.0)
        val (inc, add) = listOf(increased, flat).map { it.values(index).single() }
        val base = sword.base.first { it.code == "BASE_PHYSICAL_DAMAGE" }.values.single()
        val shown = calc.itemBase(sword, listOf(increased, flat))[PHYSICAL]
        assertEquals(tenths((base + add) * (1 + inc / 100)), shown)
        assertEquals(shown, calc.raw(emptyMap(), calc.foldItem(sword, listOf(increased, flat)))[PHYSICAL])

        val general = StatOperation(GenericDamage.STAT, Op.INCREASED, 20.0)
        val sheet = calc.raw(emptyMap(), listOf(StatOperation(PHYSICAL, Op.ADD, 100.0), StatOperation(PHYSICAL, Op.INCREASED, 30.0), general))
        assertEquals(150.0, sheet[PHYSICAL])
        assertEquals(0.0, sheet[GenericDamage.STAT] ?: 0.0)
        assertEquals(150.0, index.campaign.combat.critical.effective(150.0, null))
    }

    /**
     * Раскрытие скрытого аффикса держит правило «одна группа на предмет»: гибрид, повторяющий эффект строки копии
     * (натиск и скорость передвижения рядом со скоростью передвижения), не предлагается, и раскрытая копия групп не повторяет.
     */
    @Test
    fun unveilingNeverRepeatsAGroupOfTheItem() {
        val veils = Veils(index)
        val template = index.templates.values.first { !it.unique && it.tables.isNotEmpty() && !it.slot.isJewelLike && !it.slot.isFlask && !it.slot.isTool }
        val hybrids = index.definitions.filter { it.veiled && it.affix && index.groups(it).size > 1 }
        assertTrue(hybrids.isNotEmpty(), "no veiled hybrid repeats an affix")
        hybrids.forEach { hybrid ->
            val twin = index.definitions.first { it.affix && !it.veiled && !it.crafted && it.rolls && it.groupKey != hybrid.groupKey && it.groupKey in index.groups(hybrid) }
            val veil = if (hybrid.source == Source.PREFIX) Veils.PREFIX else Veils.SUFFIX
            repeat(40) { seed ->
                val item = ItemInstance("i", template.code, Rarity.RARE, listOf(Roll(twin.code, twin.tiers.size, 0.5), Roll(veil, 1, 0.0)))
                val offered = veils.offer(item, template, Dice(seed.toLong())).item.unveil
                assertTrue(offered.isNotEmpty() && offered.none { it.code == hybrid.code }, "${hybrid.code} offered next to ${twin.code}")
                val groups = veils.reveal(item, template, seed % offered.size).item.rolls.mapNotNull { index.modifier(it.code) }.flatMap(index::groups)
                assertEquals(groups.size, groups.toSet().size, "a group twice after unveiling next to ${twin.code}: $groups")
            }
        }
    }

    private companion object {
        const val PHYSICAL = "STOCK_ATTACK_PHYSICAL"
        const val CORRUPTION_TABLES = "corruption:"
    }
}

package com.sperance.exileforge.rules

import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.ContentLoader
import com.sperance.exileforge.rules.content.Orb
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.roll.Dice
import com.sperance.exileforge.rules.roll.ItemFactory
import com.sperance.exileforge.rules.roll.ItemInstance
import com.sperance.exileforge.rules.roll.OrbApplier
import com.sperance.exileforge.rules.run.Run
import com.sperance.exileforge.rules.run.RunContext
import com.sperance.exileforge.rules.run.RunTally
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Правила, которые нельзя нарушить ни одним путём: волшебная и редкая копия не пуста - ни из фабрики,
 * ни после любой сферы; заход не выдаёт одну и ту же награду дважды и одинаково катится у клиента и сервера.
 */
class RulesInvariantsTest {
    private val index: ContentIndex by lazy {
        val resources = File(System.getProperty("content.resources") ?: "../src/main/resources")
        ContentLoader.load { File(resources, "content/$it").readText() }
    }

    private fun affixes(item: ItemInstance) = item.rolls.count { index.modifier(it.code)?.affix == true }

    private fun floorHeld(item: ItemInstance): Boolean {
        val template = index.template(item.template) ?: return true
        if (item.rarity != Rarity.UNCOMMON && item.rarity != Rarity.RARE) return true
        return affixes(item) >= index.limits(item.rarity, template.slot).floor
    }

    @Test
    fun magicAndRareAreNeverEmptyAfterAnyOrb() {
        val factory = ItemFactory(index)
        val orbs = OrbApplier(index)
        val templates = index.templates.values.filter { !it.unique && it.tables.isNotEmpty() }.distinctBy { it.slot }
        var n = 0
        templates.forEach { template ->
            listOf(Rarity.UNCOMMON, Rarity.RARE).forEach { rarity ->
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

    @Test
    fun aRunNeverPaysTheSameRewardTwice() {
        val zone = index.zones.values.first { it.boss.isNotBlank() && it.chestLoot.isNotBlank() }
        val context = RunContext("", zone.level)
        val run = Run(index, zone, 99L, context)
        val chests = List(3) { run.chest() }
        val ids = chests.flatMap { reward -> reward.equipment.map { it.id } } + List(2) { run.boss() }.flatMap { reward -> reward.equipment.map { it.id } }
        assertEquals(ids.size, ids.toSet().size, "item ids repeat: $ids")
        assertEquals(RunTally(chests = 3, bosses = 2), run.tally)
        // Сервер строит заход со счётом, который сохранил: следующая награда - та же, что у клиента
        val replay = Run(index, zone, 99L, context, RunTally(chests = 3, bosses = 2))
        assertEquals(run.chest(), replay.chest())
    }
}

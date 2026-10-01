package com.sperance.exileforge.rules

import com.sperance.exileforge.rules.content.AuctionRules
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.ContentLoader
import com.sperance.exileforge.rules.content.MAP_TEMPLATE
import com.sperance.exileforge.rules.content.MapStat
import com.sperance.exileforge.rules.content.Omen
import com.sperance.exileforge.rules.content.Orb
import com.sperance.exileforge.rules.content.Pet
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.content.Source
import com.sperance.exileforge.rules.roll.Dice
import com.sperance.exileforge.rules.roll.ItemFactory
import com.sperance.exileforge.rules.roll.LootRoller
import com.sperance.exileforge.rules.roll.OrbApplier
import com.sperance.exileforge.rules.roll.OrbTarget
import com.sperance.exileforge.rules.roll.PetOutcome
import com.sperance.exileforge.rules.sheet.SheetCalculator
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Ремесло 1.65.0: сферы на питомце, алхимия карты, сфера качества, знамение выбора, валюта аукциона. */
class CraftingTest {
    private val index: ContentIndex by lazy {
        val resources = File(System.getProperty("content.resources") ?: "../src/main/resources")
        ContentLoader.load { File(resources, "content/$it").readText() }
    }
    private val orbs by lazy { OrbApplier(index) }
    private val factory by lazy { ItemFactory(index) }

    private fun pet(dice: Dice, orb: Orb, pet: Pet, omen: Omen? = null): Pet = (orbs.apply(orb, OrbTarget.Beast(pet), dice, omen) as PetOutcome).pet

    @Test
    fun craftingOrbsChangeAPetsRarityAndLines() {
        val dice = Dice(3L)
        val species = index.pets.species.first().code
        val common = Pet("p", species)
        val magic = pet(dice, Orb.ORB_OF_TRANSMUTATION, common)
        assertEquals(Rarity.MAGIC, magic.rarity)
        assertTrue(magic.lines.isNotEmpty())
        val rare = pet(dice, Orb.REGAL_ORB, magic)
        assertEquals(Rarity.RARE, rare.rarity)
        assertTrue(rare.lines.size >= index.pets.rarities.getValue(Rarity.RARE).floor)
        val fractured = pet(dice, Orb.FRACTURING_ORB, rare)
        assertEquals(1, fractured.lines.count { it.fractured })
        assertTrue(pet(dice, Orb.CHAOS_ORB, fractured).lines.any { it.fractured })
        assertEquals(Rarity.MAGIC, pet(dice, Orb.ORB_OF_SCOURING, fractured).rarity)
        assertEquals(Rarity.COMMON, pet(dice, Orb.ORB_OF_SCOURING, rare).rarity)
        val quality = pet(dice, Orb.QUALITY_ORB, rare)
        assertTrue(quality.quality > 0)
        val corrupted = pet(dice, Orb.VAAL_ORB, rare)
        assertTrue(corrupted.corrupted)
        assertFailsWith<RuleViolation> { pet(dice, Orb.CHAOS_ORB, corrupted) }
        assertFailsWith<RuleViolation> { pet(dice, Orb.ORB_OF_ALTERATION, rare) }
    }

    @Test
    fun alchemyOnAMapAddsDifferentAlchemyLinesUpToTheCap() {
        val dice = Dice(5L)
        val template = index.template(MAP_TEMPLATE)!!
        val map = factory.create("m", template, Rarity.MAGIC, dice)
        repeat(index.rules.orbs.maxAlchemyLines) { orbs.apply(Orb.ORB_OF_ALCHEMY, map, template, dice) { "new" } }
        val alchemy = map.rolls.mapNotNull { index.modifier(it.code) }.filter { it.source == Source.ALCHEMY }
        assertEquals(index.rules.orbs.maxAlchemyLines, alchemy.size)
        assertEquals(alchemy.size, alchemy.map { it.code }.toSet().size)
        assertFailsWith<RuleViolation> { orbs.apply(Orb.ORB_OF_ALCHEMY, map, template, dice) { "new" } }
    }

    @Test
    fun qualityOrbRaisesAToolsWorkAndAMapsQuantity() {
        val dice = Dice(9L)
        val toolTemplate = index.templates.values.first { it.slot.isTool && !it.unique && it.tables.isNotEmpty() }
        val tool = factory.create("t", toolTemplate, Rarity.MAGIC, dice)
        val calc = SheetCalculator(index)
        val before = calc.toolOperations(tool).sumOf { it.value }
        orbs.apply(Orb.QUALITY_ORB, tool, toolTemplate, dice) { "new" }
        assertEquals(index.rules.quality.step(Rarity.MAGIC), tool.quality)
        assertTrue(calc.toolOperations(tool).sumOf { it.value } > before)
        assertFailsWith<RuleViolation> { orbs.apply(Orb.QUALITY_ORB, tool, toolTemplate, dice, Omen.CATALYST_LIFE) { "new" } }

        val mapTemplate = index.template(MAP_TEMPLATE)!!
        val map = factory.create("m", mapTemplate, Rarity.MAGIC, dice)
        val loot = LootRoller(index)
        val quantity = loot.mapEffects(map)[MapStat.QUANTITY.code] ?: 0.0
        orbs.apply(Orb.QUALITY_ORB, map, mapTemplate, dice) { "new" }
        assertEquals(quantity + map.quality, loot.mapEffects(map)[MapStat.QUANTITY.code])
    }

    @Test
    fun omenOfChoiceOffersThreeAndThePickIsAdded() {
        val dice = Dice(11L)
        val template = index.templates.values.first { !it.unique && it.tables.isNotEmpty() && !it.slot.isJewelLike && !it.slot.isFlask && !it.slot.isTool }
        val item = factory.create("i", template, Rarity.RARE, dice)
        item.rolls = item.rolls.filter { index.modifier(it.code)?.affix != true } + item.rolls.filter { index.modifier(it.code)?.affix == true }.take(index.limits(Rarity.RARE, template.slot).floor)
        val affixes = item.rolls.count { index.modifier(it.code)?.affix == true }
        orbs.apply(Orb.EXALTED_ORB, item, template, dice, Omen.CHOICE) { "new" }
        assertEquals(index.rules.orbs.choices, item.offer.size)
        assertEquals(affixes, item.rolls.count { index.modifier(it.code)?.affix == true })
        val picked = item.offer[1]
        orbs.choose(item, template, 1)
        assertTrue(picked in item.rolls && item.offer.isEmpty())
        assertFailsWith<RuleViolation> { orbs.choose(item, template, 0) }
    }

    @Test
    fun auctionTradesOnlyInBaseCraftingOrbs() {
        val rules = index.rules.auction
        assertEquals(AuctionRules.BASE_CURRENCIES, rules.currencies)
        AuctionRules.BASE_CURRENCIES.forEach { assertTrue(rules.trades(it.name)) }
        listOf(Orb.MIRROR_OF_KALANDRA.name, Orb.QUALITY_ORB.name, Orb.VAAL_ORB.name, Omen.CATALYST_LIFE.code, Omen.CHOICE.code, "PET_ORB_GROWTH")
            .forEach { assertTrue(!rules.trades(it), it) }
    }
}

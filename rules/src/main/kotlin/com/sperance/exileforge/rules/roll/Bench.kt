package com.sperance.exileforge.rules.roll

import com.sperance.exileforge.rules.RuleViolation
import com.sperance.exileforge.rules.content.BenchRecipe
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.ItemTemplate
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.content.Source
import com.sperance.exileforge.rules.text.LocaleKey

/**
 * Верстак: игрок сам выбирает строку и платит сферами. Верстачная строка на копии одна, встаёт на
 * свободное место своего вида и не рядом со своей группой; снимается за сферу правил.
 */
class Bench(private val index: ContentIndex, private val affixes: AffixRoller = AffixRoller(index)) {
    private val craftable = setOf(Rarity.MAGIC, Rarity.RARE)

    /** Один незнакомый герою рецепт тира этой локации, если такой есть. */
    fun draw(known: Collection<String>, level: Int, dice: Dice): BenchRecipe? =
        dice.pickOrNull(index.bench.filter { it.tier == index.rules.bench.tierFor(level) && it.code !in known })

    fun craft(item: ItemInstance, template: ItemTemplate, recipe: BenchRecipe, known: Collection<String>, dice: Dice): OrbOutcome {
        requireModifiable(item, template)
        if (recipe.code !in known) throw RuleViolation("CR_024", listOf(recipe.code))
        if (item.rarity !in craftable) throw RuleViolation("CR_005", listOf(item.rarity.name))
        if (!recipe.fits(template.slot)) throw RuleViolation("CR_019", listOf(template.slot.name))
        val current = affixes.definitions(item.rolls)
        if (current.any { it.crafted }) throw RuleViolation("CR_015", listOf(name(template)))
        if (current.any { it.groupKey == recipe.group }) throw RuleViolation("CR_016", listOf(name(template)))
        val (prefixes, suffixes) = affixes.freeSlots(item.rarity, current, template.slot)
        if ((if (recipe.source == Source.PREFIX) prefixes else suffixes) == 0) throw RuleViolation("CR_007", listOf(name(template)))
        item.rolls = item.rolls + Roll(recipe.modifier, recipe.tier, dice.share())
        return OrbOutcome(item, null, "currency.crafted", listOf(name(template)))
    }

    fun uncraft(item: ItemInstance, template: ItemTemplate): OrbOutcome {
        requireModifiable(item, template)
        val crafted = item.rolls.filter(affixes::isCrafted)
        if (crafted.isEmpty()) throw RuleViolation("CR_017", listOf(name(template)))
        val left = item.rolls.count { affixes.isAffix(it) && !affixes.isCrafted(it) }
        if (left < index.limits(item.rarity, template.slot).floor) throw RuleViolation("CR_025", listOf(name(template), LocaleKey.rarity(item.rarity)))
        if (template.slot.isJewelLike && left == 0) throw RuleViolation("CR_023", listOf(name(template)))
        item.rolls = item.rolls - crafted.toSet()
        return OrbOutcome(item, null, "currency.uncrafted", listOf(name(template)))
    }

    private fun requireModifiable(item: ItemInstance, template: ItemTemplate) {
        if (item.corrupted) throw RuleViolation("CR_004", listOf(name(template)))
        if (item.mirrored) throw RuleViolation("CR_010", listOf(name(template)))
    }

    private fun name(template: ItemTemplate) = LocaleKey.equipmentName(template.code)
}

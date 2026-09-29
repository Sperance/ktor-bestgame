package com.sperance.exileforge.rules.roll

import com.sperance.exileforge.rules.RuleViolation
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.Essence
import com.sperance.exileforge.rules.content.Influence
import com.sperance.exileforge.rules.content.ItemTemplate
import com.sperance.exileforge.rules.content.ModifierDef
import com.sperance.exileforge.rules.content.Orb
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.content.Source
import com.sperance.exileforge.rules.table.Tables
import com.sperance.exileforge.rules.text.LocaleKey

/** Чем кончилась сфера: копия после неё, новая копия (Зеркало) и ключ сообщения с аргументами-ключами. */
data class OrbOutcome(val item: ItemInstance, val created: ItemInstance? = null, val messageKey: String, val messageArgs: List<String> = emptyList())

/** Исходы сферы Ваал, равновероятные. */
enum class VaalOutcome { NOTHING, IMPLICIT, RARE, SHIFT }

/**
 * Сферы PoE над копией: только правила - что сфера требует и как меняет. Копия меняется на месте,
 * списание сферы и запись - забота вызывающего.
 */
class OrbApplier(private val index: ContentIndex, private val affixes: AffixRoller = AffixRoller(index), private val factory: ItemFactory = ItemFactory(index, affixes)) {
    private val rules get() = index.rules

    fun apply(orb: Orb, item: ItemInstance, template: ItemTemplate, dice: Dice, newId: () -> String): OrbOutcome {
        val outcome = applyRule(orb, item, template, dice, newId)
        if (template.slot.isJewelLike && outcome.item.id == item.id && factory.empty(template, outcome.item)) throw RuleViolation("CR_023", listOf(name(template)))
        return outcome
    }

    private fun applyRule(orb: Orb, item: ItemInstance, template: ItemTemplate, dice: Dice, newId: () -> String): OrbOutcome {
        if (item.corrupted) throw RuleViolation("CR_004", listOf(name(template)))
        if (item.mirrored) throw RuleViolation("CR_010", listOf(name(template)))
        if (orb.mapOnly && template.slot != com.sperance.exileforge.rules.content.Slot.MAP) throw RuleViolation("CR_020", listOf(orbName(orb)))
        if (if (template.slot.isFlask) orb !in rules.flasks.orbs else orb.flaskOnly) throw RuleViolation("CR_027", listOf(orbName(orb), name(template)))
        return when (orb) {
            Orb.ORB_OF_TRANSMUTATION -> upgrade(item, template, Rarity.COMMON, Rarity.UNCOMMON, dice)
            // Алхимия катит от дна редкой до потолка без одного: полный набор - только сферами сверху и ремеслом
            Orb.ORB_OF_ALCHEMY -> upgrade(item, template, Rarity.COMMON, Rarity.RARE, dice, below = 1)
            Orb.ORB_OF_ALTERATION -> reroll(item, template, Rarity.UNCOMMON, dice)
            Orb.CHAOS_ORB -> reroll(item, template, Rarity.RARE, dice)
            Orb.ORB_OF_AUGMENTATION -> augment(item, template, Rarity.UNCOMMON, dice)
            Orb.EXALTED_ORB -> augment(item, template, Rarity.RARE, dice)
            Orb.REGAL_ORB -> regal(item, template, dice)
            Orb.DIVINE_ORB -> divine(item, template, dice)
            Orb.BLESSED_ORB -> blessed(item, template, dice)
            Orb.ORB_OF_ANNULMENT -> annul(item, template, dice)
            Orb.ORB_OF_SCOURING -> scour(item, template)
            Orb.VAAL_ORB -> vaal(item, template, dice)
            Orb.ORB_OF_CHANCE -> chance(item, template, dice)
            Orb.MIRROR_OF_KALANDRA -> mirror(item, template, newId)
            Orb.FRACTURING_ORB -> fracture(item, template, dice)
            Orb.SHAPERS_ORB, Orb.ELDER_ORB, Orb.ABYSS_ORB -> influence(item, template, orb.influence!!, dice)
            Orb.ORB_OF_REGRET -> throw RuleViolation("CR_009", listOf(orb.name))
            Orb.EMPOWERING_ORB -> empower(item, template, dice)
            Orb.MERCY_ORB -> mercy(item, template, dice)
            Orb.PERIL_ORB -> peril(item, template, dice)
            Orb.GLASSBLOWERS_BAUBLE -> bauble(item, template)
            Orb.HELMET_SCROLL, Orb.GLOVES_SCROLL, Orb.BOOTS_SCROLL, Orb.WEAPON_SCROLL -> enchant(item, template, orb, dice)
            else -> alchemyLine(item, template, orb.alchemyLine ?: throw RuleViolation("CR_009", listOf(orb.name)), dice)
        }
    }

    /**
     * Эссенция: обычная вещь становится редкой с гарантированной строкой на тире её ступени; ступень,
     * что перебрасывает редкие, и особая берут и редкую. Волшебную, уникальную, флягу, карту, самоцвет не берёт.
     */
    /**
     * Пойдёт ли сфера на копию: пробный бросок над её копией, отказ правила - нет. Кузница клиента
     * показывает только такие сферы; сама копия не меняется.
     */
    fun accepts(orb: Orb, item: ItemInstance, template: ItemTemplate): Boolean =
        runCatching { apply(orb, item.copy(), template, Dice(PROBE)) { PROBE_ID } }.isSuccess

    /** То же для эссенции. */
    fun accepts(essence: Essence, item: ItemInstance, template: ItemTemplate): Boolean =
        runCatching { applyEssence(essence, item.copy(), template, Dice(PROBE)) }.isSuccess

    fun applyEssence(essence: Essence, item: ItemInstance, template: ItemTemplate, dice: Dice): OrbOutcome {
        val essenceName = LocaleKey.itemName(essence.code)
        if (item.corrupted) throw RuleViolation("CR_004", listOf(name(template)))
        if (item.mirrored) throw RuleViolation("CR_010", listOf(name(template)))
        val line = essence.guarantee(template.slot) ?: throw RuleViolation("CR_029", listOf(essenceName, name(template)))
        val tiers = index.essences.tiers
        when (item.rarity) {
            Rarity.COMMON -> Unit
            Rarity.RARE -> if (!essence.special && !tiers[essence.tier - 1].rerollsRare) throw RuleViolation("CR_030", listOf(essenceName))
            else -> throw RuleViolation("CR_029", listOf(essenceName, name(template)))
        }
        val share = if (essence.special) 1.0 else (essence.tier - 1).toDouble() / (tiers.size - 1)
        val forced = affixes.rollShare(line, share, dice) ?: throw RuleViolation("CR_029", listOf(essenceName, name(template)))
        val group = affixes.definitions(listOf(forced)).map { it.groupKey }.toSet()
        val kept = affixes.fractured(item.rolls).filterNot { held -> affixes.definitions(listOf(held)).any { it.groupKey in group } }
        val around = if (affixes.isAffix(forced)) kept + forced else kept
        item.rarity = Rarity.RARE
        item.rolls = affixes.permanent(item.rolls) + kept + forced + affixes.rollAffixes(template, Rarity.RARE, dice, item.influence, around, level = item.level(template))
        return outcome(item, template, "currency.essence", essenceName)
    }

    private fun upgrade(item: ItemInstance, template: ItemTemplate, from: Rarity, to: Rarity, dice: Dice, below: Int = 0): OrbOutcome {
        requireRarity(item, template, from)
        item.rarity = to
        item.rolls = affixes.permanent(item.rolls) + affixes.rollAffixes(template, to, dice, item.influence, below = below, level = item.level(template))
        return outcome(item, template, "currency.upgraded", LocaleKey.rarity(to), affixes.affixes(item.rolls).size.toString())
    }

    private fun reroll(item: ItemInstance, template: ItemTemplate, required: Rarity, dice: Dice): OrbOutcome {
        requireRarity(item, template, required)
        val kept = affixes.fractured(item.rolls)
        item.rolls = affixes.permanent(item.rolls) + kept + affixes.rollAffixes(template, item.rarity, dice, item.influence, kept, level = item.level(template))
        return outcome(item, template, "currency.rerolled", affixes.affixes(item.rolls).size.toString())
    }

    private fun augment(item: ItemInstance, template: ItemTemplate, required: Rarity, dice: Dice): OrbOutcome {
        requireRarity(item, template, required)
        val added = affixes.rollExtraAffix(template, item.rarity, item.rolls, dice, item.influence, item.level(template)) ?: throw RuleViolation("CR_007", listOf(name(template)))
        item.rolls = item.rolls + added
        return outcome(item, template, "currency.augmented")
    }

    private fun regal(item: ItemInstance, template: ItemTemplate, dice: Dice): OrbOutcome {
        requireRarity(item, template, Rarity.UNCOMMON)
        item.rarity = Rarity.RARE
        affixes.rollExtraAffix(template, item.rarity, item.rolls, dice, item.influence, item.level(template))?.let { item.rolls = item.rolls + it }
        affixes.normalize(template, item.rarity, item.rolls, dice, item.influence, item.level(template))?.let { item.rolls = it }
        return outcome(item, template, "currency.regal", affixes.affixes(item.rolls).size.toString())
    }

    private fun divine(item: ItemInstance, template: ItemTemplate, dice: Dice): OrbOutcome {
        val rerollable: (ModifierDef) -> Boolean = if (item.rarity.fixed) { { it.source == Source.UNIQUE } } else { { it.affix } }
        val count = affixes.definitions(item.rolls.filterNot { it.fractured }).count(rerollable)
        if (count == 0) throw RuleViolation("CR_006", listOf(name(template)))
        item.rolls = affixes.rerollShares(item.rolls, dice, rerollable)
        return outcome(item, template, "currency.divine", count.toString())
    }

    private fun blessed(item: ItemInstance, template: ItemTemplate, dice: Dice): OrbOutcome {
        if (affixes.definitions(item.rolls).none { it.source == Source.IMPLICIT }) throw RuleViolation("CR_008", listOf(name(template)))
        item.rolls = affixes.rerollShares(item.rolls, dice) { it.source == Source.IMPLICIT }
        return outcome(item, template, "currency.blessed")
    }

    private fun annul(item: ItemInstance, template: ItemTemplate, dice: Dice): OrbOutcome {
        val current = affixes.affixes(item.rolls).filterNot { it.fractured }
        if (current.isEmpty()) throw RuleViolation("CR_006", listOf(name(template)))
        if (affixes.affixes(item.rolls).size <= index.limits(item.rarity, template.slot).floor) throw RuleViolation("CR_025", listOf(name(template), LocaleKey.rarity(item.rarity)))
        item.rolls = item.rolls - dice.pick(current)
        return outcome(item, template, "currency.annulled", affixes.affixes(item.rolls).size.toString())
    }

    private fun scour(item: ItemInstance, template: ItemTemplate): OrbOutcome {
        if (item.rarity.fixed) throw RuleViolation("CR_005", listOf(item.rarity.name))
        val kept = affixes.fractured(item.rolls)
        val target = if (kept.isEmpty()) Rarity.COMMON else Rarity.UNCOMMON
        if (item.rarity == target && affixes.affixes(item.rolls).size == kept.size) throw RuleViolation("CR_006", listOf(name(template)))
        item.rarity = target
        item.rolls = affixes.permanent(item.rolls) + kept
        return outcome(item, template, if (kept.isEmpty()) "currency.scoured" else "currency.scoured_fractured")
    }

    private fun vaal(item: ItemInstance, template: ItemTemplate, dice: Dice): OrbOutcome {
        item.corrupted = true
        return when (dice.pick(VaalOutcome.entries)) {
            VaalOutcome.NOTHING -> outcome(item, template, "currency.vaal_nothing")
            VaalOutcome.IMPLICIT -> {
                val corruption = affixes.rollFrom(listOf(AffixRoller.corruptionTag(template.slot)), item.level(template), dice)
                if (corruption != null) item.rolls = item.rolls.filterNot { affixes.definition(it)?.source == Source.IMPLICIT } + corruption
                outcome(item, template, if (corruption != null) "currency.vaal_modifier" else "currency.vaal_nothing")
            }
            VaalOutcome.RARE -> if (item.rarity.fixed || template.slot.isFlask) outcome(item, template, "currency.vaal_nothing") else {
                val kept = affixes.fractured(item.rolls)
                item.rarity = Rarity.RARE
                item.rolls = affixes.permanent(item.rolls) + kept + affixes.rollAffixes(template, Rarity.RARE, dice, item.influence, kept, level = item.level(template))
                outcome(item, template, "currency.vaal_rare", affixes.affixes(item.rolls).size.toString())
            }
            VaalOutcome.SHIFT -> {
                val (low, high) = rules.orbs.vaalShift
                item.rolls = item.rolls.map { roll -> roll.copy(scale = com.sperance.exileforge.rules.content.tenths((roll.scale ?: 1.0) * (low + (high - low) * dice.nextDouble())).let { Math.round(it * 1000) / 1000.0 }) }
                outcome(item, template, "currency.vaal_shift")
            }
        }
    }

    private fun chance(item: ItemInstance, template: ItemTemplate, dice: Dice): OrbOutcome {
        requireRarity(item, template, Rarity.COMMON)
        // Уникалка не выше уровня базы и её дальности (1.18.0): из базы первого уровня не выйдет вещь семидесятого
        val unique = Tables.draw(index.templatePoolUpTo(rules.orbs.chanceUniques, template.level + index.rules.loot.uniqueReach).filter { it.value.slot == template.slot }, dice)
        if (unique != null && dice.percent(rules.orbs.chanceUniquePercent)) {
            val reborn = factory.create(item.id, unique, Rarity.UNIQUE, dice, level = item.level(template))
            return OrbOutcome(reborn.also { it.slot = item.slot; it.socket = item.socket; it.quality = item.quality }, null, "currency.chance_unique", listOf(name(template), name(unique)))
        }
        val wanted = Tables.value<Rarity>(index.tables, rules.orbs.chanceRarities, dice) ?: Rarity.COMMON
        val rarity = factory.rarityFor(template, wanted)
        item.rarity = rarity
        item.rolls = affixes.permanent(item.rolls) + affixes.rollAffixes(template, rarity, dice, item.influence, level = item.level(template))
        return outcome(item, template, "currency.chance_rarity", LocaleKey.rarity(rarity))
    }

    private fun mirror(item: ItemInstance, template: ItemTemplate, newId: () -> String): OrbOutcome =
        OrbOutcome(item, item.copy(id = newId(), rolls = item.rolls.toList(), slot = null, socket = null, mirrored = true, locked = false), "currency.mirrored", listOf(name(template)))

    private fun fracture(item: ItemInstance, template: ItemTemplate, dice: Dice): OrbOutcome {
        if (item.rarity != Rarity.RARE) throw RuleViolation("CR_005", listOf(item.rarity.name))
        if (item.rolls.any { it.fractured }) throw RuleViolation("CR_011", listOf(name(template)))
        val current = affixes.affixes(item.rolls)
        if (current.size < rules.orbs.fractureMinAffixes) throw RuleViolation("CR_012", listOf(name(template)))
        val chosen = dice.pick(current.filterNot(affixes::isCrafted).ifEmpty { current })
        item.rolls = item.rolls.map { if (it === chosen) it.copy(fractured = true) else it }
        return outcome(item, template, "currency.fractured")
    }

    private fun influence(item: ItemInstance, template: ItemTemplate, influence: Influence, dice: Dice): OrbOutcome {
        if (!template.slot.influenceable) throw RuleViolation("CR_013", listOf(name(template)))
        if (item.rarity != Rarity.RARE) throw RuleViolation("CR_005", listOf(item.rarity.name))
        if (item.influence != null) throw RuleViolation("CR_014", listOf(name(template)))
        val added = affixes.rollInfluenced(template, item.rarity, item.rolls, influence, dice, item.level(template)) ?: throw RuleViolation("CR_007", listOf(name(template)))
        item.influence = influence
        item.rolls = item.rolls + added
        return outcome(item, template, "currency.influenced", LocaleKey.enumLabel("EnumInfluence", influence.name))
    }

    private fun enchant(item: ItemInstance, template: ItemTemplate, orb: Orb, dice: Dice): OrbOutcome {
        val slot = orb.enchantSlot ?: throw RuleViolation("CR_009", listOf(orb.name))
        if (template.slot.tag != slot) throw RuleViolation("CR_026", listOf(orbName(orb)))
        val enchantment = affixes.rollFrom(listOf(AffixRoller.enchantTag(slot)), item.level(template), dice) ?: throw RuleViolation("CR_026", listOf(orbName(orb)))
        item.rolls = item.rolls.filterNot { affixes.definition(it)?.source == Source.ENCHANTMENT } + enchantment
        return outcome(item, template, "currency.enchanted")
    }

    private fun bauble(item: ItemInstance, template: ItemTemplate): OrbOutcome {
        val step = baubleStep(item)
        if (step <= 0) throw RuleViolation("CR_028", listOf(name(template)))
        item.quality += step
        return outcome(item, template, "currency.bauble", item.quality.toString())
    }

    /** Сколько качества прибавит «Стеклодув»: два процента обычной фляге, один волшебной, до потолка. */
    fun baubleStep(item: ItemInstance): Int {
        val step = when (item.rarity) { Rarity.COMMON -> rules.flasks.baubleCommon; Rarity.UNCOMMON -> rules.flasks.baubleMagic; else -> 0 }
        return step.coerceAtMost(rules.flasks.maxQuality - item.quality).coerceAtLeast(0)
    }

    private fun harmful(def: ModifierDef): Boolean = def.effects.any { (index.campaign.maps.risk[it.stat] ?: 0.0) > 0 }

    private fun empower(item: ItemInstance, template: ItemTemplate, dice: Dice): OrbOutcome {
        var raised = 0
        item.rolls = item.rolls.map { roll -> if (!affixes.isAffix(roll)) roll else affixes.raiseTier(roll, dice)?.also { raised++ } ?: roll }
        if (raised == 0) throw RuleViolation("CR_006", listOf(name(template)))
        return outcome(item, template, "currency.empowered")
    }

    private fun mercy(item: ItemInstance, template: ItemTemplate, dice: Dice): OrbOutcome {
        val candidates = affixes.affixes(item.rolls).filterNot { it.fractured }.filter { roll -> affixes.definition(roll)?.let(::harmful) == true }
        if (candidates.isEmpty()) throw RuleViolation("CR_021", listOf(name(template)))
        if (affixes.affixes(item.rolls).size <= index.limits(item.rarity, template.slot).floor) throw RuleViolation("CR_025", listOf(name(template), LocaleKey.rarity(item.rarity)))
        item.rolls = item.rolls - dice.pick(candidates)
        return outcome(item, template, "currency.mercy")
    }

    /** Вредный аффикс обычным роллером одного аффикса: потолок редкости, места префиксов и суффиксов и группы держатся. */
    private fun peril(item: ItemInstance, template: ItemTemplate, dice: Dice): OrbOutcome {
        if (item.rarity.fixed || affixes.affixes(item.rolls).size >= index.limits(item.rarity, template.slot).ceiling) throw RuleViolation("CR_007", listOf(name(template)))
        val pool = affixes.affixPool(template).filter { (def) -> harmful(def) }
        val added = affixes.rollExtraFrom(pool, template, item.rarity, item.rolls, dice, item.level(template)) ?: throw RuleViolation("CR_021", listOf(name(template)))
        item.rolls = item.rolls + added
        return outcome(item, template, "currency.peril")
    }

    private fun alchemyLine(item: ItemInstance, template: ItemTemplate, code: String, dice: Dice): OrbOutcome {
        val lines = affixes.definitions(item.rolls).filter { it.source == Source.ALCHEMY }
        if (lines.size >= rules.orbs.maxAlchemyLines || lines.any { it.code == code }) throw RuleViolation("CR_022", listOf(name(template)))
        item.rolls = item.rolls + (affixes.rollCode(code, item.level(template), dice) ?: throw RuleViolation("CR_022", listOf(name(template))))
        return outcome(item, template, "currency.alchemy_line")
    }

    private fun requireRarity(item: ItemInstance, template: ItemTemplate, required: Rarity) {
        if (item.rarity != required) throw RuleViolation("CR_005", listOf("${template.code}: ${item.rarity}, need $required"))
    }

    private fun outcome(item: ItemInstance, template: ItemTemplate, key: String, vararg args: String) = OrbOutcome(item, null, key, listOf(name(template)) + args)
    private fun name(template: ItemTemplate) = LocaleKey.equipmentName(template.code)
    private fun orbName(orb: Orb) = LocaleKey.enumLabel("EnumCurrencyOrb", orb.name)

    private companion object {
        const val PROBE = 0L
        const val PROBE_ID = "probe"
    }
}

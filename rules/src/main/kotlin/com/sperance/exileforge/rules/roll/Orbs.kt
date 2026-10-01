package com.sperance.exileforge.rules.roll

import com.sperance.exileforge.rules.RuleViolation
import com.sperance.exileforge.rules.content.Catalyst
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.Essence
import com.sperance.exileforge.rules.content.Influence
import com.sperance.exileforge.rules.content.ItemTemplate
import com.sperance.exileforge.rules.content.ModifierDef
import com.sperance.exileforge.rules.content.Omen
import com.sperance.exileforge.rules.content.Orb
import com.sperance.exileforge.rules.content.Pet
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.content.Slot
import com.sperance.exileforge.rules.content.Source
import com.sperance.exileforge.rules.table.Tables
import com.sperance.exileforge.rules.text.LocaleKey

/** Чем кончилась сфера над целью: сообщение с аргументами-ключами. */
sealed interface Forged {
    val messageKey: String
    val messageArgs: List<String>
}

/** Чем кончилась сфера над вещью: копия после неё, новая копия (Зеркало) и ключ сообщения с аргументами-ключами. */
data class OrbOutcome(val item: ItemInstance, val created: ItemInstance? = null, override val messageKey: String, override val messageArgs: List<String> = emptyList()) : Forged

/** Чем кончилась сфера над питомцем (1.65.0): питомец после неё. */
data class PetOutcome(val pet: Pet, override val messageKey: String, override val messageArgs: List<String> = emptyList()) : Forged

/** Над чем работает сфера (1.65.0): одна точка входа для кузницы вещей и зверинца - сферы ремесла меняют и тех, и других. */
sealed interface OrbTarget {
    data class Gear(val item: ItemInstance, val template: ItemTemplate) : OrbTarget
    data class Beast(val pet: Pet) : OrbTarget
}

/** Исходы сферы Ваал, равновероятные. */
enum class VaalOutcome { NOTHING, IMPLICIT, RARE, SHIFT }

/**
 * Сферы PoE над копией: только правила - что сфера требует и как меняет. Копия меняется на месте,
 * списание сферы и запись - забота вызывающего.
 */
class OrbApplier(private val index: ContentIndex, private val affixes: AffixRoller = AffixRoller(index), private val factory: ItemFactory = ItemFactory(index, affixes)) {
    private val rules get() = index.rules

    private val veils = Veils(index, affixes)
    private val choices = Choices(index, affixes)
    private val pets = PetForge(index)

    /** Сфера [orb] на цель [target] - вещь или питомца - со знамением [omen]; питомец не меняется на месте - новый в итоге. */
    fun apply(orb: Orb, target: OrbTarget, dice: Dice, omen: Omen? = null, newId: () -> String = { throw RuleViolation("CR_009", listOf(orb.name)) }): Forged = when (target) {
        is OrbTarget.Gear -> apply(orb, target.item, target.template, dice, omen, newId)
        is OrbTarget.Beast -> apply(orb, target.pet, dice, omen)
    }

    /** Сфера [orb] на питомца [pet] со знамением [omen] (1.65.0). */
    fun apply(orb: Orb, pet: Pet, dice: Dice, omen: Omen? = null): PetOutcome {
        requireOmen(orb, omen)
        return pets.apply(orb, pet, dice, omen)
    }

    /** Пойдёт ли сфера на цель: пробный бросок над копией. */
    fun accepts(orb: Orb, target: OrbTarget, omen: Omen? = null): Boolean = when (target) {
        is OrbTarget.Gear -> accepts(orb, target.item, target.template, omen)
        is OrbTarget.Beast -> runCatching { apply(orb, target, Dice(PROBE), omen) }.isSuccess
    }

    /** Сфера [orb] на копию; [omen] (1.35.0) - знамение, потраченное вместе с ней и меняющее её действие. */
    fun apply(orb: Orb, item: ItemInstance, template: ItemTemplate, dice: Dice, omen: Omen? = null, newId: () -> String): OrbOutcome {
        requireOmen(orb, omen)
        val outcome = applyRule(orb, item, template, dice, omen, newId)
        if (template.slot.isJewelLike && outcome.item.id == item.id && factory.empty(template, outcome.item)) throw RuleViolation("CR_023", listOf(name(template)))
        return outcome
    }

    private fun requireOmen(orb: Orb, omen: Omen?) {
        if (omen != null && !omen.fits(orb)) throw RuleViolation("CR_031", listOf(orbName(orb)))
    }

    private fun applyRule(orb: Orb, item: ItemInstance, template: ItemTemplate, dice: Dice, omen: Omen?, newId: () -> String): OrbOutcome {
        if (item.corrupted) throw RuleViolation("CR_004", listOf(name(template)))
        if (item.mirrored) throw RuleViolation("CR_010", listOf(name(template)))
        if (template.slot.isFlask && orb !in rules.flasks.orbs) throw RuleViolation("CR_027", listOf(orbName(orb), name(template)))
        // Выбор, что ждал игрока, снимает любая следующая сфера (1.65.0): варианты не переживают перемену копии.
        item.offer = emptyList()
        val choice = omen == Omen.CHOICE
        if (template.slot == Slot.MAP) when (orb) {
            Orb.ORB_OF_ALCHEMY -> return alchemyLine(item, template, dice, choice)
            Orb.ORB_OF_SCOURING -> return scourMap(item, template, dice)
            Orb.DIVINE_ORB -> if (omen == Omen.TIER) return empower(item, template, dice)
            else -> Unit
        }
        return when (orb) {
            Orb.ORB_OF_TRANSMUTATION -> upgrade(item, template, Rarity.COMMON, Rarity.MAGIC, dice)
            // Алхимия катит от дна редкой до потолка без одного: полный набор - только сферами сверху и ремеслом
            Orb.ORB_OF_ALCHEMY -> if (choice) alchemyChoice(item, template, dice) else upgrade(item, template, Rarity.COMMON, Rarity.RARE, dice, below = 1)
            Orb.ORB_OF_ALTERATION -> reroll(item, template, Rarity.MAGIC, dice)
            Orb.CHAOS_ORB -> reroll(item, template, Rarity.RARE, dice, omen?.side)
            Orb.ORB_OF_AUGMENTATION -> augment(item, template, Rarity.MAGIC, dice)
            Orb.EXALTED_ORB -> if (choice) exaltChoice(item, template, dice) else augment(item, template, Rarity.RARE, dice, omen?.side, twice = omen == Omen.GREATER_EXALTATION)
            Orb.REGAL_ORB -> regal(item, template, dice, omen?.side)
            Orb.DIVINE_ORB -> if (omen == Omen.TIER) raiseOne(item, template, dice) else divine(item, template, dice)
            Orb.BLESSED_ORB -> blessed(item, template, dice)
            Orb.ORB_OF_ANNULMENT -> annul(item, template, dice, omen)
            Orb.ORB_OF_SCOURING -> scour(item, template)
            Orb.VAAL_ORB -> vaal(item, template, dice, sure = omen == Omen.CORRUPTION)
            Orb.ORB_OF_CHANCE -> chance(item, template, dice)
            Orb.MIRROR_OF_KALANDRA -> mirror(item, template, newId)
            Orb.FRACTURING_ORB -> fracture(item, template, dice)
            Orb.SHAPERS_ORB, Orb.ELDER_ORB, Orb.ABYSS_ORB -> influence(item, template, orb.influence!!, dice)
            Orb.ORB_OF_REGRET -> throw RuleViolation("CR_009", listOf(orb.name))
            Orb.UNVEILING_ORB -> veils.offer(item, template, dice)
            Orb.QUALITY_ORB -> quality(item, template, omen?.catalyst)
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
    fun accepts(orb: Orb, item: ItemInstance, template: ItemTemplate, omen: Omen? = null): Boolean =
        runCatching { apply(orb, item.copy(), template, Dice(PROBE), omen) { PROBE_ID } }.isSuccess

    /** Раскрытие скрытого аффикса выбором игрока [choice] из предложенных сферой раскрытия (1.35.0). */
    fun reveal(item: ItemInstance, template: ItemTemplate, choice: Int): OrbOutcome = veils.reveal(item, template, choice)

    /** Строка, выбранная игроком [choice] из вариантов знамения выбора (1.65.0). */
    fun choose(item: ItemInstance, template: ItemTemplate, choice: Int): OrbOutcome = choices.choose(item, template, choice)

    /** То же для питомца. */
    fun choose(pet: Pet, choice: Int): PetOutcome = pets.choose(pet, choice)

    /**
     * Сфера качества (1.65.0): без катализатора - база оружия и брони, фляга (до потолка фляг), инструмент (его строки труда), карта
     * (количество добычи); катализатор [catalyst] - модификаторы вида на любой копии, кроме фляги, карты и инструмента. Другой вид
     * качества на копии начинает счёт заново. Шаг по редкости, до потолка.
     */
    private fun quality(item: ItemInstance, template: ItemTemplate, catalyst: Catalyst?): OrbOutcome {
        val slot = template.slot
        val fits = if (catalyst != null) !slot.isFlask && slot != Slot.MAP && !slot.isTool
            else slot.isWeapon || slot.isArmour || slot.isFlask || slot.isTool || slot == Slot.MAP
        if (!fits) throw RuleViolation("CR_033", listOf(name(template)))
        val max = if (slot.isFlask) rules.flasks.maxQuality else rules.quality.max
        val current = if (item.catalyst == catalyst) item.quality else 0
        if (current >= max) throw RuleViolation("CR_033", listOf(name(template)))
        item.catalyst = catalyst
        item.quality = (current + rules.quality.step(item.rarity)).coerceAtMost(max)
        return outcome(item, template, if (catalyst == null) "currency.quality" else "currency.catalyst", item.quality.toString())
    }

    /** То же для эссенции. */
    fun accepts(essence: Essence, item: ItemInstance, template: ItemTemplate): Boolean =
        runCatching { applyEssence(essence, item.copy(), template, Dice(PROBE)) }.isSuccess

    fun applyEssence(essence: Essence, item: ItemInstance, template: ItemTemplate, dice: Dice): OrbOutcome {
        val essenceName = LocaleKey.itemName(essence.code)
        if (item.corrupted) throw RuleViolation("CR_004", listOf(name(template)))
        if (item.mirrored) throw RuleViolation("CR_010", listOf(name(template)))
        item.offer = emptyList()
        val lines = essence.guarantees(template).ifEmpty { throw RuleViolation("CR_029", listOf(essenceName, name(template))) }
        val tiers = index.essences.tiers
        when (item.rarity) {
            Rarity.COMMON -> Unit
            Rarity.RARE -> if (!essence.special && !tiers[essence.tier - 1].rerollsRare) throw RuleViolation("CR_030", listOf(essenceName))
            else -> throw RuleViolation("CR_029", listOf(essenceName, name(template)))
        }
        val share = if (essence.special) 1.0 else (essence.tier - 1).toDouble() / (tiers.size - 1)
        val forced = lines.firstNotNullOfOrNull { affixes.rollShare(it, share, dice, item.level(template)) } ?: throw RuleViolation("CR_029", listOf(essenceName, name(template)))
        // Закреплённый аффикс той же группы уступает гарантии; имплисит - нет: эссенция его группы на копию не идёт
        val permanent = affixes.permanent(item.rolls)
        val taken = affixes.groups(listOf(forced))
        if (affixes.groups(permanent).any { it in taken }) throw RuleViolation("CR_016", listOf(name(template)))
        val kept = affixes.fractured(item.rolls).filter { held -> affixes.groups(listOf(held)).none { it in taken } }
        val around = permanent + kept + forced
        item.rarity = Rarity.RARE
        item.rolls = around + affixes.rollAffixes(template, Rarity.RARE, dice, item.influence, around, level = item.level(template))
        return outcome(item, template, "currency.essence", essenceName)
    }

    private fun upgrade(item: ItemInstance, template: ItemTemplate, from: Rarity, to: Rarity, dice: Dice, below: Int = 0): OrbOutcome {
        requireRarity(item, template, from)
        item.rarity = to
        val permanent = affixes.permanent(item.rolls)
        item.rolls = permanent + affixes.rollAffixes(template, to, dice, item.influence, permanent, below = below, level = item.level(template))
        return outcome(item, template, "currency.upgraded", LocaleKey.rarity(to), affixes.affixes(item.rolls).size.toString())
    }

    private fun reroll(item: ItemInstance, template: ItemTemplate, required: Rarity, dice: Dice, side: Source? = null): OrbOutcome {
        requireRarity(item, template, required)
        // Знамение стороны (1.35.0): другая сторона остаётся как есть, перекатывается только своя.
        val kept = affixes.permanent(item.rolls) + affixes.fractured(item.rolls) +
            if (side == null) emptyList() else affixes.affixes(item.rolls).filter { !it.fractured && affixes.definition(it)?.source != side }
        item.rolls = kept + affixes.rollAffixes(template, item.rarity, dice, item.influence, kept, level = item.level(template), side = side)
        return outcome(item, template, "currency.rerolled", affixes.affixes(item.rolls).size.toString())
    }

    private fun augment(item: ItemInstance, template: ItemTemplate, required: Rarity, dice: Dice, side: Source? = null, twice: Boolean = false): OrbOutcome {
        requireRarity(item, template, required)
        val added = affixes.rollExtraAffix(template, item.rarity, item.rolls, dice, item.influence, item.level(template), side) ?: throw RuleViolation("CR_007", listOf(name(template)))
        item.rolls = item.rolls + added
        if (twice) affixes.rollExtraAffix(template, item.rarity, item.rolls, dice, item.influence, item.level(template), side)?.let { item.rolls = item.rolls + it }
        return outcome(item, template, "currency.augmented")
    }

    private fun regal(item: ItemInstance, template: ItemTemplate, dice: Dice, side: Source? = null): OrbOutcome {
        requireRarity(item, template, Rarity.MAGIC)
        item.rarity = Rarity.RARE
        affixes.rollExtraAffix(template, item.rarity, item.rolls, dice, item.influence, item.level(template), side)?.let { item.rolls = item.rolls + it }
        affixes.normalize(template, item.rarity, item.rolls, dice, item.influence, item.level(template))?.let { item.rolls = it }
        return outcome(item, template, "currency.regal", affixes.affixes(item.rolls).size.toString())
    }

    /**
     * Алхимия со знамением выбора (1.65.0): редкая с дном аффиксов до потолка без двух - и варианты ещё одного на выбор; итог тот
     * же, что у простой алхимии, только последняя строка - выбор игрока.
     */
    private fun alchemyChoice(item: ItemInstance, template: ItemTemplate, dice: Dice): OrbOutcome {
        requireRarity(item, template, Rarity.COMMON)
        item.rarity = Rarity.RARE
        val permanent = affixes.permanent(item.rolls)
        item.rolls = permanent + affixes.rollAffixes(template, Rarity.RARE, dice, item.influence, permanent, below = 2, level = item.level(template))
        val options = affixes.candidates(template, item.rarity, item.rolls, dice, item.influence, item.level(template), rules.orbs.choices)
        return if (options.isNotEmpty()) offer(item, template, options)
            else outcome(item, template, "currency.upgraded", LocaleKey.rarity(item.rarity), affixes.affixes(item.rolls).size.toString())
    }

    /** Возвышение со знамением выбора (1.65.0): вместо случайного аффикса - варианты на выбор. */
    private fun exaltChoice(item: ItemInstance, template: ItemTemplate, dice: Dice): OrbOutcome {
        requireRarity(item, template, Rarity.RARE)
        val options = affixes.candidates(template, item.rarity, item.rolls, dice, item.influence, item.level(template), rules.orbs.choices)
        if (options.isEmpty()) throw RuleViolation("CR_007", listOf(name(template)))
        return offer(item, template, options)
    }

    /** Варианты [options] ждут выбора игрока ([choose]). */
    private fun offer(item: ItemInstance, template: ItemTemplate, options: List<Roll>): OrbOutcome {
        item.offer = options
        return outcome(item, template, "currency.choice_offer", options.size.toString())
    }

    /** Божественная со знамением тира (1.65.0): один случайный аффикс - на тир выше, в пределах уровня вещи. */
    private fun raiseOne(item: ItemInstance, template: ItemTemplate, dice: Dice): OrbOutcome {
        val level = item.level(template)
        val target = dice.shuffled(affixes.affixes(item.rolls)).firstNotNullOfOrNull { roll -> affixes.raiseTier(roll, dice, level)?.let { roll to it } }
            ?: throw RuleViolation("CR_006", listOf(name(template)))
        item.rolls = item.rolls.map { if (it === target.first) target.second else it }
        return outcome(item, template, "currency.tier_raised")
    }

    /** Очищение карты (1.65.0): обычной карта не бывает - волшебная с одним случайным аффиксом (закреплённые остаются). */
    private fun scourMap(item: ItemInstance, template: ItemTemplate, dice: Dice): OrbOutcome {
        val fractured = affixes.fractured(item.rolls)
        val kept = affixes.permanent(item.rolls) + fractured
        // Волшебная карта, на которой лишь закреплённые аффиксы, осталась бы прежней: сфера не тратится.
        if (item.rarity == Rarity.MAGIC && fractured.isNotEmpty() && item.rolls.size == kept.size) throw RuleViolation("CR_006", listOf(name(template)))
        item.rarity = Rarity.MAGIC
        item.rolls = kept + listOfNotNull(if (fractured.isEmpty()) affixes.rollExtraAffix(template, Rarity.MAGIC, kept, dice, item.influence, item.level(template)) else null)
        return outcome(item, template, "currency.scoured_map")
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

    private fun annul(item: ItemInstance, template: ItemTemplate, dice: Dice, omen: Omen? = null): OrbOutcome {
        // Знамения (1.35.0): стороны - только её аффиксы, Света - только ремесленный.
        val current = affixes.affixes(item.rolls).filterNot { it.fractured }.filter { roll ->
            val def = affixes.definition(roll)
            when {
                omen == Omen.LIGHT -> def?.crafted == true
                omen?.side != null -> def?.source == omen.side
                else -> true
            }
        }
        if (current.isEmpty()) throw RuleViolation("CR_006", listOf(name(template)))
        if (affixes.affixes(item.rolls).size <= index.limits(item.rarity, template.slot).floor) throw RuleViolation("CR_025", listOf(name(template), LocaleKey.rarity(item.rarity)))
        item.rolls = item.rolls - dice.pick(current)
        return outcome(item, template, "currency.annulled", affixes.affixes(item.rolls).size.toString())
    }

    private fun scour(item: ItemInstance, template: ItemTemplate): OrbOutcome {
        if (item.rarity.fixed) throw RuleViolation("CR_005", listOf(item.rarity.name))
        val kept = affixes.fractured(item.rolls)
        val target = if (kept.isEmpty()) Rarity.COMMON else Rarity.MAGIC
        if (item.rarity == target && affixes.affixes(item.rolls).size == kept.size) throw RuleViolation("CR_006", listOf(name(template)))
        item.rarity = target
        item.rolls = affixes.permanent(item.rolls) + kept
        return outcome(item, template, if (kept.isEmpty()) "currency.scoured" else "currency.scoured_fractured")
    }

    private fun vaal(item: ItemInstance, template: ItemTemplate, dice: Dice, sure: Boolean = false): OrbOutcome {
        item.corrupted = true
        // Осквернённую копию больше не меняет ничто, так что ждущие выборы теряют смысл.
        item.unveil = emptyList()
        item.offer = emptyList()
        // Знамение порчи (1.35.0): порча не проходит впустую.
        return when (dice.pick(if (sure) VaalOutcome.entries - VaalOutcome.NOTHING else VaalOutcome.entries)) {
            VaalOutcome.NOTHING -> outcome(item, template, "currency.vaal_nothing")
            VaalOutcome.IMPLICIT -> {
                val rest = item.rolls.filterNot { affixes.definition(it)?.source == Source.IMPLICIT }
                val corruption = affixes.rollFrom(listOf(AffixRoller.corruptionTag(template.slot)), item.level(template), dice, rest)
                if (corruption != null) item.rolls = rest + corruption
                outcome(item, template, if (corruption != null) "currency.vaal_modifier" else "currency.vaal_nothing")
            }
            VaalOutcome.RARE -> if (item.rarity.fixed || template.slot.isFlask) outcome(item, template, "currency.vaal_nothing") else {
                val kept = affixes.permanent(item.rolls) + affixes.fractured(item.rolls)
                item.rarity = Rarity.RARE
                item.rolls = kept + affixes.rollAffixes(template, Rarity.RARE, dice, item.influence, kept, level = item.level(template))
                outcome(item, template, "currency.vaal_rare", affixes.affixes(item.rolls).size.toString())
            }
            VaalOutcome.SHIFT -> {
                val (low, high) = rules.orbs.vaalShift
                // Сдвиг (1.53.0) только аффиксов: имплиситы и закреплённые строки уникальных остаются в своём диапазоне
                item.rolls = item.rolls.map { roll -> if (!affixes.isAffix(roll)) roll else roll.copy(scale = com.sperance.exileforge.rules.content.tenths((roll.scale ?: 1.0) * (low + (high - low) * dice.nextDouble())).let { Math.round(it * 1000) / 1000.0 }) }
                outcome(item, template, "currency.vaal_shift")
            }
        }
    }

    private fun chance(item: ItemInstance, template: ItemTemplate, dice: Dice): OrbOutcome {
        requireRarity(item, template, Rarity.COMMON)
        // Уникалка не выше уровня базы и её дальности (1.18.0): из базы первого уровня не выйдет вещь семидесятого.
        // Качество переходит вместе со своим видом: качество катализатора не становится качеством базы.
        val unique = Tables.draw(index.templatePoolUpTo(rules.orbs.chanceUniques, template.level + index.rules.loot.uniqueReach).filter { it.value.slot == template.slot }, dice)
        if (unique != null && dice.percent(rules.orbs.chanceUniquePercent)) {
            val reborn = factory.create(item.id, unique, Rarity.UNIQUE, dice, level = item.level(template))
            return OrbOutcome(reborn.inheriting(item), null, "currency.chance_unique", listOf(name(template), name(unique)))
        }
        val wanted = Tables.value<Rarity>(index.tables, rules.orbs.chanceRarities, dice) ?: Rarity.COMMON
        val rarity = factory.rarityFor(template, wanted)
        item.rarity = rarity
        val permanent = affixes.permanent(item.rolls)
        item.rolls = permanent + affixes.rollAffixes(template, rarity, dice, item.influence, permanent, level = item.level(template))
        return outcome(item, template, "currency.chance_rarity", LocaleKey.rarity(rarity))
    }

    private fun mirror(item: ItemInstance, template: ItemTemplate, newId: () -> String): OrbOutcome =
        OrbOutcome(item, item.copy(id = newId(), rolls = item.rolls.toList(), slot = null, socket = null, mirrored = true, locked = false, unveil = emptyList(), offer = emptyList()), "currency.mirrored", listOf(name(template)))

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
        // Карта (1.50.0) захватывается влиянием целиком: ни строк, ни тира сфера не трогает; Бездна (1.65.0) открывает на ней расщелины.
        if (template.slot == Slot.MAP) {
            if (!index.campaign.maps.influence.accepts(influence)) throw RuleViolation("CR_013", listOf(name(template)))
            if (item.influence != null) throw RuleViolation("CR_014", listOf(name(template)))
            item.influence = influence
            return outcome(item, template, "currency.influenced", LocaleKey.enumLabel("EnumInfluence", influence.name))
        }
        // Инструмент (1.65.0) берёт Создателя и Древнего - их строки труда; Бездны у инструментов нет.
        if (!template.slot.influenceable && !(template.slot.isTool && influence != Influence.ABYSS)) throw RuleViolation("CR_013", listOf(name(template)))
        if (item.rarity != Rarity.RARE) throw RuleViolation("CR_005", listOf(item.rarity.name))
        if (item.influence != null) throw RuleViolation("CR_014", listOf(name(template)))
        val added = affixes.rollInfluenced(template, item.rarity, item.rolls, influence, dice, item.level(template)) ?: throw RuleViolation("CR_007", listOf(name(template)))
        item.influence = influence
        item.rolls = item.rolls + added
        return outcome(item, template, "currency.influenced", LocaleKey.enumLabel("EnumInfluence", influence.name))
    }

    /** Божественная со знамением тира на карте (1.65.0): каждый аффикс - на тир выше, в пределах уровня карты. */
    private fun empower(item: ItemInstance, template: ItemTemplate, dice: Dice): OrbOutcome {
        var raised = 0
        item.rolls = item.rolls.map { roll -> if (!affixes.isAffix(roll)) roll else affixes.raiseTier(roll, dice, item.level(template))?.also { raised++ } ?: roll }
        if (raised == 0) throw RuleViolation("CR_006", listOf(name(template)))
        return outcome(item, template, "currency.empowered")
    }

    /**
     * Сфера алхимии на карте (1.65.0): строка «Алхимия» из таблицы [com.sperance.exileforge.rules.content.OrbRules.mapAlchemy], которой
     * на карте ещё нет, до потолка; со знамением выбора - варианты на выбор.
     */
    private fun alchemyLine(item: ItemInstance, template: ItemTemplate, dice: Dice, choice: Boolean): OrbOutcome {
        val pool = choices.mapAlchemyPool(item).toMutableList()
        if (pool.isEmpty()) throw RuleViolation("CR_022", listOf(name(template)))
        if (choice) {
            val options = mutableListOf<Roll>()
            while (options.size < rules.orbs.choices) {
                val def = Tables.draw(pool, dice) ?: break
                pool.removeAll { it.value.code == def.code }
                affixes.roll(def, item.level(template), dice)?.let(options::add)
            }
            return offer(item, template, options.ifEmpty { throw RuleViolation("CR_022", listOf(name(template))) })
        }
        val def = Tables.draw(pool, dice) ?: throw RuleViolation("CR_022", listOf(name(template)))
        item.rolls = item.rolls + (affixes.roll(def, item.level(template), dice) ?: throw RuleViolation("CR_022", listOf(name(template))))
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

package com.sperance.exileforge.rules.roll

import com.sperance.exileforge.rules.RuleViolation
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.Omen
import com.sperance.exileforge.rules.content.Orb
import com.sperance.exileforge.rules.content.Pet
import com.sperance.exileforge.rules.content.PetLine
import com.sperance.exileforge.rules.content.PetRarityRule
import com.sperance.exileforge.rules.content.PetSpecies
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.table.Tables
import com.sperance.exileforge.rules.text.LocaleKey

/**
 * Сферы ремесла на питомце (1.65.0) - вместо прежних сфер питомцев: редкость (обычный - без строк, волшебный и редкий - от дна до
 * потолка своих строк), строки и их доли меняются так же, как аффиксы вещи. Закреплённая строка не снимается и не перекатывается,
 * испорченного питомца сферы больше не меняют. Питомец не меняется на месте: итог - новая копия; волшебный и редкий не опускаются
 * ниже дна своей редкости ни одной сферой.
 */
class PetForge(private val index: ContentIndex, private val menagerie: Menagerie = Menagerie(index)) {

    fun apply(orb: Orb, pet: Pet, dice: Dice, omen: Omen? = null): PetOutcome {
        val name = name(pet)
        if (pet.corrupted) throw RuleViolation("CR_004", listOf(name))
        val kind = menagerie.species(pet.species) ?: throw RuleViolation("CR_027", listOf(orbName(orb), name))
        val choice = omen == Omen.CHOICE
        // Знамение стороны, света и катализатор питомцу не по силам: у его строк нет сторон, ремесла и видов.
        if (omen != null && omen != Omen.CHOICE && omen != Omen.CORRUPTION) throw RuleViolation("CR_027", listOf(LocaleKey.itemName(omen.code), name))
        val fresh = pet.copy(offer = emptyList())
        val next: Pet = when (orb) {
            Orb.ORB_OF_TRANSMUTATION -> upgrade(fresh, kind, Rarity.COMMON, Rarity.MAGIC, dice)

            Orb.ORB_OF_ALCHEMY -> if (choice) {
                offer(upgrade(fresh, kind, Rarity.COMMON, Rarity.RARE, dice, choice = true), kind, dice, required = false)
            } else {
                upgrade(fresh, kind, Rarity.COMMON, Rarity.RARE, dice)
            }

            Orb.REGAL_ORB -> {
                require(fresh, Rarity.MAGIC)
                val rare = rule(Rarity.RARE)
                fresh.copy(rarity = Rarity.RARE, lines = menagerie.roll(kind, maxOf(rare.floor, fresh.lines.size + 1).coerceAtMost(rare.ceiling), fresh.lines, dice))
            }

            Orb.ORB_OF_ALTERATION -> reroll(require(fresh, Rarity.MAGIC), kind, dice)

            Orb.CHAOS_ORB -> reroll(require(fresh, Rarity.RARE), kind, dice)

            Orb.ORB_OF_AUGMENTATION -> augment(require(fresh, Rarity.MAGIC), kind, dice)

            Orb.EXALTED_ORB -> if (choice) offer(roomFor(require(fresh, Rarity.RARE)), kind, dice) else augment(require(fresh, Rarity.RARE), kind, dice)

            Orb.DIVINE_ORB -> {
                if (fresh.lines.none { !it.fractured }) throw RuleViolation("CR_006", listOf(name))
                fresh.copy(lines = fresh.lines.map { line -> if (line.fractured) line else line.copy(shares = line.shares.map { dice.share() }) })
            }

            Orb.ORB_OF_ANNULMENT -> {
                val removable = fresh.lines.filterNot { it.fractured }
                if (removable.isEmpty()) throw RuleViolation("CR_006", listOf(name))
                if (fresh.lines.size <= rule(fresh.rarity).floor) throw RuleViolation("CR_025", listOf(name, LocaleKey.rarity(fresh.rarity)))
                fresh.copy(lines = fresh.lines - dice.pick(removable))
            }

            Orb.ORB_OF_SCOURING -> {
                val kept = fresh.lines.filter { it.fractured }
                val target = if (kept.isEmpty()) Rarity.COMMON else Rarity.MAGIC
                if (fresh.rarity == target && fresh.lines.size == kept.size) throw RuleViolation("CR_006", listOf(name))
                fresh.copy(rarity = target, lines = kept)
            }

            Orb.VAAL_ORB -> vaal(fresh, kind, dice, sure = omen == Omen.CORRUPTION)

            Orb.ORB_OF_CHANCE -> {
                require(fresh, Rarity.COMMON)
                val rarity = Tables.draw(Menagerie.RARITIES - Rarity.COMMON, { index.pets.rarities[it]?.weight ?: 0.0 }, dice) ?: Rarity.MAGIC
                fresh.copy(rarity = rarity, lines = menagerie.roll(kind, dice.between(rule(rarity).lines), emptyList(), dice))
            }

            Orb.FRACTURING_ORB -> {
                if (fresh.lines.any { it.fractured }) throw RuleViolation("CR_011", listOf(name))
                if (fresh.lines.isEmpty()) throw RuleViolation("CR_006", listOf(name))
                val chosen = dice.pick(fresh.lines)
                fresh.copy(lines = fresh.lines.map { if (it === chosen) it.copy(fractured = true) else it })
            }

            Orb.QUALITY_ORB -> {
                val max = index.rules.quality.max
                if (fresh.quality >= max) throw RuleViolation("CR_033", listOf(name))
                fresh.copy(quality = (fresh.quality + index.rules.quality.step(fresh.rarity)).coerceAtMost(max))
            }

            Orb.BLESSED_ORB, Orb.MIRROR_OF_KALANDRA, Orb.SHAPERS_ORB, Orb.ELDER_ORB, Orb.ABYSS_ORB, Orb.ORB_OF_REGRET, Orb.UNVEILING_ORB ->
                throw RuleViolation("CR_027", listOf(orbName(orb), name))
        }
        if (next.lines.size < rule(next.rarity).floor) throw RuleViolation("CR_025", listOf(name, LocaleKey.rarity(next.rarity)))
        return PetOutcome(next, if (next.offer.isNotEmpty()) "currency.choice_offer" else "currency.pet_changed", listOf(name) + listOfNotNull(next.offer.size.takeIf { it > 0 }?.toString()))
    }

    /** Строка, выбранная игроком [choice] из вариантов знамения выбора: встаёт, если ей ещё есть место. */
    fun choose(pet: Pet, choice: Int): PetOutcome {
        val name = name(pet)
        val option = pet.offer.getOrNull(choice) ?: throw RuleViolation("CR_034", listOf(name))
        if (pet.lines.size >= rule(pet.rarity).ceiling || pet.lines.any { it.code == option.code }) throw RuleViolation("CR_007", listOf(name))
        return PetOutcome(pet.copy(lines = pet.lines + option, offer = emptyList()), "currency.chosen", listOf(name))
    }

    /**
     * Повышение редкости с [from] до [to]: строки новой редкости; [choice] - от дна до потолка без одной, и ещё одна - выбором
     * игрока: дно держится и тогда, когда он так и не выберет.
     */
    private fun upgrade(pet: Pet, kind: PetSpecies, from: Rarity, to: Rarity, dice: Dice, choice: Boolean = false): Pet {
        require(pet, from)
        val target = rule(to)
        val count = if (choice) dice.between(target.floor, (target.ceiling - 1).coerceAtLeast(target.floor)) else dice.between(target.lines)
        return pet.copy(rarity = to, lines = menagerie.roll(kind, count, pet.lines.filter { it.fractured }, dice))
    }

    private fun reroll(pet: Pet, kind: PetSpecies, dice: Dice): Pet = pet.copy(lines = menagerie.roll(kind, dice.between(rule(pet.rarity).lines), pet.lines.filter { it.fractured }, dice))

    private fun augment(pet: Pet, kind: PetSpecies, dice: Dice): Pet {
        roomFor(pet)
        val added = menagerie.rollOne(kind, pet.lines, dice) ?: throw RuleViolation("CR_007", listOf(name(pet)))
        return pet.copy(lines = pet.lines + added)
    }

    private fun roomFor(pet: Pet): Pet {
        if (pet.lines.size >= rule(pet.rarity).ceiling) throw RuleViolation("CR_007", listOf(name(pet)))
        return pet
    }

    /** Варианты строки на выбор: разные, каких у питомца нет; без места под строку - ошибка, если выбор [required]. */
    private fun offer(pet: Pet, kind: PetSpecies, dice: Dice, required: Boolean = true): Pet {
        val options = mutableListOf<PetLine>()
        if (pet.lines.size < rule(pet.rarity).ceiling) {
            while (options.size < index.rules.orbs.choices) options += menagerie.rollOne(kind, pet.lines, dice, options.map { it.code }) ?: break
        }
        if (options.isEmpty() && required) throw RuleViolation("CR_007", listOf(name(pet)))
        return pet.copy(offer = options)
    }

    /** Порча питомца: ничего, одна строка на высшем значении или переброс строк его редкости; со знамением порчи - не впустую. */
    private fun vaal(pet: Pet, kind: PetSpecies, dice: Dice, sure: Boolean): Pet {
        val corrupted = pet.copy(corrupted = true)
        return when (dice.pick(if (sure) PetVaal.entries - PetVaal.NOTHING else PetVaal.entries)) {
            PetVaal.NOTHING -> corrupted

            PetVaal.STRONG -> corrupted.lines.filterNot { it.fractured }.takeIf { it.isNotEmpty() }?.let(dice::pick)
                ?.let { strong -> corrupted.copy(lines = corrupted.lines.map { if (it === strong) it.copy(shares = it.shares.map { 1.0 }) else it }) } ?: corrupted

            PetVaal.REROLL -> if (pet.rarity == Rarity.COMMON) corrupted else reroll(corrupted, kind, dice)
        }
    }

    private fun require(pet: Pet, rarity: Rarity): Pet {
        if (pet.rarity != rarity) throw RuleViolation("CR_005", listOf("${pet.species}: ${pet.rarity}, need $rarity"))
        return pet
    }

    private fun rule(rarity: Rarity): PetRarityRule = menagerie.rule(rarity) ?: throw RuleViolation("CR_005", listOf(rarity.name))
    private fun name(pet: Pet) = "pet.${pet.species}"
    private fun orbName(orb: Orb) = LocaleKey.enumLabel("EnumCurrencyOrb", orb.name)

    /** Исходы порчи питомца, равновероятные. */
    private enum class PetVaal { NOTHING, STRONG, REROLL }
}

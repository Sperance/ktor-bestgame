package com.sperance.exileforge.rules.roll

import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.Line
import com.sperance.exileforge.rules.content.Pet
import com.sperance.exileforge.rules.content.PetKind
import com.sperance.exileforge.rules.content.PetLine
import com.sperance.exileforge.rules.content.PetLineRule
import com.sperance.exileforge.rules.content.PetOrbAction
import com.sperance.exileforge.rules.content.PetSpecies
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.sheet.SheetCalculator
import com.sperance.exileforge.rules.table.Tables
import kotlin.math.max
import kotlin.math.pow

/**
 * Правила питомцев (1.5.0) - одни на сервере и клиенте: яйцо вылупляется видом своего биома, сферы питомцев
 * меняют его, как сферы вещей меняют вещь, опыт боёв растит уровень. Волшебный и редкий питомец не
 * остаются без строк: ни вылупление, ни сфера не опускают их ниже дна редкости.
 */
class Menagerie(private val index: ContentIndex) {
    private val file get() = index.pets
    private val species: Map<String, PetSpecies> = file.species.associateBy { it.code }
    val maxLevel: Int get() = index.classes.maxLevel

    fun species(code: String): PetSpecies? = species[code]

    /** Яйцо зоны биома [biome]. */
    fun eggOf(biome: String): String? = file.eggs[biome]

    fun isEgg(code: String): Boolean = code in file.eggs.values

    fun orb(code: String): PetOrbAction? = file.orbs[code]

    /** Из яйца [egg] - питомец [id]: вид его биома по весам, редкость по весам, строки её числа. */
    fun hatch(egg: String, id: String, dice: Dice): Pet? {
        val biome = file.eggs.entries.firstOrNull { it.value == egg }?.key ?: return null
        val kind = Tables.draw(file.species.filter { it.biome == biome }, PetSpecies::weight, dice) ?: return null
        val rarity = Tables.draw(RARITIES, { file.rarities[it]?.weight ?: 0.0 }, dice) ?: Rarity.COMMON
        val rule = file.rarities.getValue(rarity)
        return Pet(id, kind.code, rarity, lines = roll(kind, dice.between(rule.lines), emptyList(), dice))
    }

    /** Сфера [action] на питомце: новый питомец или null, если сфера на нём ничего не делает. */
    fun apply(action: PetOrbAction, pet: Pet, dice: Dice): Pet? {
        val kind = species(pet.species) ?: return null
        val rule = file.rarities[pet.rarity] ?: return null
        return when (action) {
            PetOrbAction.UPGRADE -> {
                val next = RARITIES.getOrNull(RARITIES.indexOf(pet.rarity) + 1) ?: return null
                val nextRule = file.rarities.getValue(next)
                pet.copy(rarity = next, lines = roll(kind, max(nextRule.floor, pet.lines.size + 1).coerceAtMost(nextRule.ceiling), pet.lines, dice))
            }
            PetOrbAction.REROLL -> if (pet.rarity == Rarity.COMMON) null else pet.copy(lines = roll(kind, dice.between(rule.lines), emptyList(), dice))
            PetOrbAction.AUGMENT -> if (pet.lines.size >= rule.ceiling || pool(kind).size <= pet.lines.size) null
                else pet.copy(lines = roll(kind, pet.lines.size + 1, pet.lines, dice))
            PetOrbAction.DIVINE -> if (pet.lines.isEmpty()) null else pet.copy(lines = pet.lines.map { line -> line.copy(shares = line.shares.map { dice.share() }) })
            PetOrbAction.ANNUL -> if (pet.lines.size <= rule.floor) null else pet.copy(lines = pet.lines.toMutableList().apply { removeAt(dice.nextInt(size)) })
            PetOrbAction.GROWTH -> if (pet.level >= maxLevel) null else gain(pet, (index.classes.threshold(pet.level + 1) ?: pet.experience) - pet.experience)
        }?.takeIf { it.lines.size >= (file.rarities[it.rarity]?.floor ?: 0) }
    }

    /** Опыт питомцу: уровень только растёт, до потолка героя. */
    fun gain(pet: Pet, amount: Double): Pet {
        if (amount <= 0) return pet
        val total = pet.experience + amount
        return pet.copy(experience = total, level = max(pet.level, index.classes.levelOf(total)))
    }

    /** Строки питомца значениями его уровня. */
    fun lines(pet: Pet): List<Line> {
        val kind = species(pet.species) ?: return emptyList()
        val growth = 1 + file.lineGrowth * (pet.level - 1).coerceAtLeast(0)
        val rules = pool(kind).associateBy { it.code }
        return pet.lines.mapNotNull { line ->
            val rule = rules[line.code] ?: return@mapNotNull null
            Line(line.code, rule.values.mapIndexed { i, range ->
                val low = range[0]; val high = range.getOrElse(1) { low }
                Math.round((low + (high - low) * (line.shares.getOrNull(i) ?: 0.0)) * growth * 10) / 10.0
            })
        }
    }

    /** Строки помощников герою: то, что ляжет на его лист рядом с деревом. */
    fun helperLines(pets: Collection<Pet>): List<Line> = pets.filter { species(it.species)?.kind == PetKind.HELPER }.flatMap(::lines)

    /** Лист боевого питомца: лист роли на его уровне, как растёт монстр, удар его стихией, строки сверху. */
    fun sheet(pet: Pet): Map<String, Double> {
        val kind = species(pet.species) ?: return emptyMap()
        val role = kind.role ?: return emptyMap()
        val campaign = index.campaign
        val steps = campaign.growthTaper.steps(pet.level)
        val base = file.roles[role].orEmpty().entries.associate { (stat, value) ->
            val key = if (stat == ATTACK_BASE) "STOCK_ATTACK_${kind.element ?: "PHYSICAL"}" else stat
            key to value * (campaign.growth[key] ?: 1.0).pow(steps)
        }
        val calculator = SheetCalculator(index)
        return calculator.compute(campaign.defaults + base, calculator.expand(lines(pet)))
    }

    /** Лечение героя поддержкой, % здоровья в секунду. */
    fun supportHeal(pet: Pet): Double = file.supportHeal[0] * (1 + file.supportHeal.getOrElse(1) { 0.0 } * (pet.level - 1))

    fun pool(kind: PetSpecies): List<PetLineRule> = file.lines.filter { it.kind == kind.kind && (kind.kind == PetKind.COMBAT || it.focus == kind.focus) }

    /** Строки до [count]: [keep] остаются, новые - из пула без повторов по весам. */
    private fun roll(kind: PetSpecies, count: Int, keep: List<PetLine>, dice: Dice): List<PetLine> {
        val lines = keep.toMutableList()
        while (lines.size < count) {
            val taken = lines.mapTo(HashSet()) { it.code }
            val rule = Tables.draw(pool(kind).filter { it.code !in taken }, PetLineRule::weight, dice) ?: break
            lines += PetLine(rule.code, rule.values.map { dice.share() })
        }
        return lines
    }

    companion object {
        val RARITIES = listOf(Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE)
        /** Ключ удара в листе роли: он становится ударом стихии вида. */
        const val ATTACK_BASE = "STOCK_ATTACK_PHYSICAL"
    }
}

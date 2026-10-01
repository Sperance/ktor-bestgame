package com.sperance.exileforge.rules.roll

import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.Line
import com.sperance.exileforge.rules.content.Pet
import com.sperance.exileforge.rules.content.PetKind
import com.sperance.exileforge.rules.content.PetLine
import com.sperance.exileforge.rules.content.PetLineRule
import com.sperance.exileforge.rules.content.PetOrbAction
import com.sperance.exileforge.rules.content.PetSpecies
import com.sperance.exileforge.rules.content.PetRarityRule
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.sheet.SheetCalculator
import com.sperance.exileforge.rules.sheet.SourceKind
import com.sperance.exileforge.rules.sheet.SourcedLine
import com.sperance.exileforge.rules.sheet.StatSource
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

    /** Правило строк редкости [rarity]: дно и потолок. */
    fun rule(rarity: Rarity): PetRarityRule? = file.rarities[rarity]

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

    /**
     * Собственная сфера питомцев [action] (1.65.0 - только рост уровня; редкость и строки меняют сферы ремесла, [PetForge]):
     * новый питомец или null, если сфера на нём ничего не делает.
     */
    fun apply(action: PetOrbAction, pet: Pet, dice: Dice): Pet? = when (action) {
        PetOrbAction.GROWTH -> if (pet.level >= maxLevel) null else gain(pet, (index.classes.threshold(pet.level + 1) ?: pet.experience) - pet.experience)
    }

    /** Опыт питомцу: уровень только растёт, до потолка героя. */
    fun gain(pet: Pet, amount: Double): Pet {
        if (amount <= 0) return pet
        val total = pet.experience + amount
        return pet.copy(experience = total, level = max(pet.level, index.classes.levelOf(total)))
    }

    /** Строки питомца значениями его уровня и качества (1.65.0: каждый процент качества - процент к значению строки). */
    fun lines(pet: Pet): List<Line> {
        val kind = species(pet.species) ?: return emptyList()
        val growth = (1 + file.lineGrowth * (pet.level - 1).coerceAtLeast(0)) * (1 + pet.quality.coerceAtLeast(0) / 100.0)
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

    /** Те же строки помощников, каждая со своим питомцем - для разбивки листа. */
    fun helperSourced(pets: Collection<Pet>): List<SourcedLine> = pets.filter { species(it.species)?.kind == PetKind.HELPER }
        .flatMap { pet -> lines(pet).map { SourcedLine(it, StatSource(SourceKind.PET, pet.id)) } }

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

    /** Одна новая строка из пула, которой у питомца нет и нет среди [also]; null - пул исчерпан. */
    fun rollOne(kind: PetSpecies, keep: List<PetLine>, dice: Dice, also: Collection<String> = emptyList()): PetLine? {
        val taken = keep.mapTo(HashSet()) { it.code } + also
        val rule = Tables.draw(pool(kind).filter { it.code !in taken }, PetLineRule::weight, dice) ?: return null
        return PetLine(rule.code, rule.values.map { dice.share() })
    }

    fun pool(kind: PetSpecies): List<PetLineRule> = file.lines.filter { it.kind == kind.kind && (kind.kind == PetKind.COMBAT || it.focus == kind.focus) }

    /** Строки до [count]: [keep] остаются, новые - из пула без повторов по весам. */
    fun roll(kind: PetSpecies, count: Int, keep: List<PetLine>, dice: Dice): List<PetLine> {
        val lines = keep.toMutableList()
        while (lines.size < count) lines += rollOne(kind, lines, dice) ?: break
        return lines
    }

    companion object {
        val RARITIES = listOf(Rarity.COMMON, Rarity.MAGIC, Rarity.RARE)
        /** Ключ удара в листе роли: он становится ударом стихии вида. */
        const val ATTACK_BASE = "STOCK_ATTACK_PHYSICAL"
    }
}

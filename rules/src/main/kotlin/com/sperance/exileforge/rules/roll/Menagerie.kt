package com.sperance.exileforge.rules.roll

import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.Incubation
import com.sperance.exileforge.rules.content.IncubatorRules
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

    /** Из яйца [egg] - питомец [id]: вид его биома по весам, редкость [rarity] (нет - по весам) и уровень [level], строки числа редкости. */
    fun hatch(egg: String, id: String, dice: Dice, rarity: Rarity = rollRarity(dice), level: Int = 1): Pet? {
        val biome = file.eggs.entries.firstOrNull { it.value == egg }?.key ?: return null
        val kind = Tables.draw(file.species.filter { it.biome == biome && it.element2 == null }, PetSpecies::weight, dice) ?: return null
        val rule = file.rarities.getValue(rarity)
        val grown = level.coerceIn(1, maxLevel)
        return Pet(id, kind.code, rarity, experience = index.classes.threshold(grown) ?: 0.0, level = grown, hatchLevel = grown, lines = roll(kind, dice.between(rule.lines), emptyList(), dice))
    }

    /**
     * Скрещивание (1.74.0): гибрид стихий родителей с шансом правила - новый питомец первого уровня с ролью одного из
     * родителей, - иначе null: тогда в сумку ложится яйцо биома одного из них ([breedEgg]).
     */
    fun breed(a: Pet, b: Pet, id: String, dice: Dice): Pet? {
        val first = species(a.species) ?: return null
        val second = species(b.species) ?: return null
        val hybrid = file.species.hybridOf(first.element ?: return null, second.element ?: return null) ?: return null
        if (!dice.chance(file.breeding.hybridChance)) return null
        val role = if (dice.chance(0.5)) a.role ?: first.role else b.role ?: second.role
        val rarity = rollRarity(dice)
        return Pet(id, hybrid.code, rarity, experience = 0.0, level = 1, lines = roll(hybrid, dice.between(file.rarities.getValue(rarity).lines), emptyList(), dice), role = role)
    }

    /** Яйцо биома одного из родителей. */
    fun breedEgg(a: Pet, b: Pet, dice: Dice): String? =
        listOfNotNull(species(a.species), species(b.species)).filter { it.element2 == null }.randomOrNullBy(dice)?.let { file.eggs[it.biome] }

    private fun <T> List<T>.randomOrNullBy(dice: Dice): T? = if (isEmpty()) null else this[dice.between(0, size - 1)]

    /** Редкость яйца по весам редкостей. */
    fun rollRarity(dice: Dice): Rarity = Tables.draw(RARITIES, { file.rarities[it]?.weight ?: 0.0 }, dice) ?: Rarity.COMMON

    /** Открытых мест инкубатора по листу героя [sheet]. */
    fun incubatorSlots(sheet: Map<String, Double>): Int = file.incubator.slots(sheet[IncubatorRules.SLOTS] ?: 0.0)

    /**
     * Закладка яйца [egg] в место [slot] (1.67.0): редкость - веса и шанс листа [sheet] поднять её на ступень, уровень - доля
     * уровня героя [heroLevel], срок - кривая инкубатора и стат срока листа. Null - не яйцо.
     */
    fun incubate(egg: String, slot: Int, heroLevel: Int, sheet: Map<String, Double>, now: Long, dice: Dice): Incubation? {
        if (!isEgg(egg)) return null
        val rules = file.incubator
        val rolled = rollRarity(dice)
        val rarity = if (dice.percent(sheet[IncubatorRules.RARITY_UP] ?: 0.0)) RARITIES.getOrElse(RARITIES.indexOf(rolled) + 1) { rolled } else rolled
        val share = rules.levelShare[0] + (rules.levelShare[1] - rules.levelShare[0]) * dice.nextDouble()
        val level = Math.round(heroLevel * share).toInt().coerceIn(1, maxLevel)
        val duration = rules.durationMillis(level, maxLevel, rarity, sheet[IncubatorRules.HATCH_TIME] ?: 0.0)
        return Incubation(slot, egg, rarity, level, now, now + duration)
    }

    /** Вылупление созревшего яйца [incubation] питомцем [id]: вид и строки катятся сейчас, редкость и уровень - закладки. */
    fun hatch(incubation: Incubation, id: String, dice: Dice): Pet? = hatch(incubation.egg, id, dice, incubation.rarity, incubation.level)

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
        val role = pet.role ?: kind.role ?: return emptyMap()
        val campaign = index.campaign
        val steps = campaign.growthTaper.steps(pet.level)
        // Гибрид (1.74.0): удар поровну двумя стихиями, весь лист роли сильнее.
        val power = if (kind.element2 != null) file.breeding.power else 1.0
        val base = file.roles[role].orEmpty().entries.flatMap { (stat, value) ->
            val keys = if (stat == ATTACK_BASE) listOfNotNull(kind.element ?: "PHYSICAL", kind.element2).map { "STOCK_ATTACK_$it" } else listOf(stat)
            keys.map { key -> key to value * power / keys.size * (campaign.growth[key] ?: 1.0).pow(steps) }
        }.toMap()
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

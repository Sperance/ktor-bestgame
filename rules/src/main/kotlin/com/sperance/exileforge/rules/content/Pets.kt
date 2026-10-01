package com.sperance.exileforge.rules.content

import com.sperance.exileforge.rules.fail
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.pow

/*
 * Питомцы (1.5.0). Файл `pets.json`: виды по биомам, их пул строк, сферы питомцев, яйца, редкости и рост.
 * Два рода: боевой - союзник в бою (роль × стихия), помощник - строки герою (ауры, добыча, ремёсла).
 * Строки питомца - обычные модификаторы контента: их текст уже есть на всех языках.
 */

@Serializable
enum class PetKind { COMBAT, HELPER }

/** Роль боевого питомца: танк держит врагов на себе, боец бьёт, поддержка лечит героя. */
@Serializable
enum class PetRole { TANK, FIGHTER, SUPPORT }

/** Чем помогает помощник: аурой герою, добычей, ремёслами. */
@Serializable
enum class PetFocus { AURA, LOOT, CRAFT }

/** Что делает собственная сфера питомцев (1.65.0): только рост уровня - редкость и строки меняют сферы ремесла вещей. */
@Serializable
enum class PetOrbAction { GROWTH }

/** Вид питомца: код (ключ `pet.<code>` локали), род, биом яйца, вес; у боевого - роль и стихия, у помощника - дело. */
@Serializable
data class PetSpecies(
    val code: String,
    val kind: PetKind,
    val biome: String,
    val weight: Double = 100.0,
    val role: PetRole? = null,
    /** Стихия удара: `PHYSICAL`, `FIRE`, `COLD`, `LIGHTNING` или `CHAOS` - стат `STOCK_ATTACK_<стихия>`. */
    val element: String? = null,
    val focus: PetFocus? = null,
)

/** Строка пула: модификатор контента, кому она (род и, для помощника, дело), значения первого уровня по эффектам и вес. */
@Serializable
data class PetLineRule(
    val code: String,
    val kind: PetKind,
    val focus: PetFocus? = null,
    val values: List<List<Double>>,
    val weight: Double = 100.0,
)

/** Сколько строк у питомца редкости: от и до. */
@Serializable
data class PetRarityRule(val lines: List<Int>, val weight: Double = 0.0) {
    val floor: Int get() = lines[0]
    val ceiling: Int get() = lines.getOrElse(1) { lines[0] }
}

@Serializable
data class PetsFile(
    val species: List<PetSpecies> = emptyList(),
    val lines: List<PetLineRule> = emptyList(),
    /** Лист боевого питомца на первом уровне по роли; растёт с уровнем, как лист монстра. */
    val roles: Map<PetRole, Map<String, Double>> = emptyMap(),
    /** Предмет сферы → её действие. */
    val orbs: Map<String, PetOrbAction> = emptyMap(),
    /** Биом → предмет яйца. */
    val eggs: Map<String, String> = emptyMap(),
    val rarities: Map<Rarity, PetRarityRule> = emptyMap(),
    /** Шанс яйца с редкого монстра и с босса, доля; количество добычи его множит. */
    val eggChance: PetEggChance = PetEggChance(),
    /** Прибавка значения строки за уровень питомца сверх первого, доля. */
    val lineGrowth: Double = 0.03,
    /** Доля опыта героя, что идёт активным питомцам. */
    val experienceShare: Double = 0.5,
    /** Лечение героя поддержкой, % его здоровья в секунду на первом уровне и прибавка за уровень. */
    val supportHeal: List<Double> = listOf(1.0, 0.02),
    /** Доля ударов врага по питомцу, что не танк (танк держит все, пока стоит). */
    val drawFire: Double = 0.3,
    /** Инкубатор (1.67.0): места, уровень вылупившегося и срок вылупления. */
    val incubator: IncubatorRules = IncubatorRules(),
)

/**
 * Инкубатор (1.67.0). Яйцо не знает ни уровня, ни редкости - их решает начало инкубации: редкость - веса редкостей
 * (и шанс [STOCK_HATCH_RARITY_UP][RARITY_UP] поднять её на ступень), уровень - уровень героя в момент закладки, умноженный
 * на долю из [levelShare] (не ниже 1): яйцо, заложенное сильным героем, растит сильного питомца.
 *
 * Срок: оценка `s = levelWeight × (уровень / потолок)^levelPower + (1 - levelWeight) × rarityScore[редкость]` в 0..1,
 * минуты - геометрически от [minMinutes] (s = 0: первый уровень, обычный) до [maxMinutes] (s = 1: потолок, редкий).
 * Стат [HATCH_TIME] (проценты, минус - быстрее) множит срок, сокращая его не больше чем на [maxReduction] процентов.
 * Мест [baseSlots] плюс стат [SLOTS], всего не больше [maxSlots].
 */
@Serializable
data class IncubatorRules(
    val baseSlots: Int = 1,
    val maxSlots: Int = 3,
    val minMinutes: Double = 5.0,
    val maxMinutes: Double = 480.0,
    val levelWeight: Double = 0.6,
    val levelPower: Double = 1.0,
    val rarityScore: Map<Rarity, Double> = mapOf(Rarity.COMMON to 0.0, Rarity.MAGIC to 0.5, Rarity.RARE to 1.0),
    val levelShare: List<Double> = listOf(0.5, 1.0),
    val maxReduction: Double = 75.0,
) {
    /** Открытых мест при стате мест [bonus] листа героя. */
    fun slots(bonus: Double): Int = (baseSlots + bonus.toInt().coerceAtLeast(0)).coerceAtMost(maxSlots)

    /** Срок вылупления, мс: питомец уровня [level] из [maxLevel] редкости [rarity] при стате срока [hatchTime] листа героя. */
    fun durationMillis(level: Int, maxLevel: Int, rarity: Rarity, hatchTime: Double): Long {
        val depth = (level.coerceAtLeast(1) - 1).toDouble() / (maxLevel - 1).coerceAtLeast(1)
        val score = (levelWeight * depth.coerceIn(0.0, 1.0).pow(levelPower) + (1 - levelWeight) * (rarityScore[rarity] ?: 0.0)).coerceIn(0.0, 1.0)
        val minutes = minMinutes * (maxMinutes / minMinutes).pow(score)
        val factor = (1 + hatchTime / 100).coerceAtLeast(1 - maxReduction / 100)
        return (minutes * factor * MINUTE).toLong()
    }

    fun validate() {
        if (baseSlots < 1 || maxSlots < baseSlots || minMinutes <= 0 || maxMinutes < minMinutes || levelWeight !in 0.0..1.0 || levelPower <= 0 ||
            maxReduction !in 0.0..99.0 || levelShare.size != 2 || levelShare[0] !in 0.0..levelShare[1] || rarityScore.values.any { it !in 0.0..1.0 }
        ) fail("pets: incubator")
    }

    companion object {
        const val SLOTS = "STOCK_INCUBATOR_SLOTS"
        const val HATCH_TIME = "STOCK_HATCH_TIME"
        const val RARITY_UP = "STOCK_HATCH_RARITY_UP"
        val STATS = listOf(SLOTS, HATCH_TIME, RARITY_UP)
        private const val MINUTE = 60_000.0
    }
}

/**
 * Яйцо в месте [slot] инкубатора (1.67.0): решённые при закладке редкость [rarity] и уровень [level], начало [startedAt]
 * и срок [readyAt] (мс эпохи). Вид и строки катятся при вылуплении - по биому яйца [egg]. Готовность считается по часам
 * на чтении: инкубатор зреет и без игрока.
 */
@Serializable
data class Incubation(
    val slot: Int,
    val egg: String,
    val rarity: Rarity,
    val level: Int,
    val startedAt: Long,
    val readyAt: Long,
) {
    fun ready(now: Long): Boolean = now >= readyAt
    fun remainingMillis(now: Long): Long = (readyAt - now).coerceAtLeast(0)
}

@Serializable
data class PetEggChance(val rare: Double = 0.02, val boss: Double = 0.2)

/**
 * Копия питомца в зверинце: вид, редкость, опыт и уровень, строки долями ролла. С 1.65.0 - порча (сферы его больше не меняют),
 * качество (каждый процент усиливает строки) и варианты строки [offer], что ждут выбора игрока после сферы со знамением выбора.
 */
@Serializable
data class Pet(
    val id: String,
    val species: String,
    val rarity: Rarity = Rarity.COMMON,
    @SerialName("xp") val experience: Double = 0.0,
    val level: Int = 1,
    val lines: List<PetLine> = emptyList(),
    val corrupted: Boolean = false,
    val quality: Int = 0,
    val offer: List<PetLine> = emptyList(),
    /** Уровень вылупления (1.67.0): от него считаются уровни, за которые платит отпуск; у питомцев до инкубатора - 1. */
    @SerialName("hl") val hatchLevel: Int = 1,
)

/** Строка питомца: модификатор и доли ролла по его эффектам; [fractured] (1.65.0) - закреплена сферой закрепления. */
@Serializable
data class PetLine(val code: String, @SerialName("p") val shares: List<Double>, @SerialName("f") val fractured: Boolean = false)

/** Проверка `pets.json` по контенту: яйца и сферы - предметы питомцев, строки - модификаторы, пулы полны на любую редкость. */
fun PetsFile.validate(index: ContentIndex) {
    if (species.isEmpty()) return
    if (species.map { it.code }.toSet().size != species.size) fail("pets: species codes")
    val biomes = index.campaign.zones.mapTo(HashSet()) { it.biome }
    if (eggs.keys != biomes) fail("pets: eggs of biomes ${biomes - eggs.keys}")
    (eggs.values + orbs.keys).forEach { if (index.item(it)?.category != Item.PET) fail("pets: item $it") }
    if (PetOrbAction.entries.toSet() != orbs.values.toSet()) fail("pets: orbs")
    if (roles.keys != PetRole.entries.toSet()) fail("pets: roles")
    listOf(Rarity.COMMON, Rarity.MAGIC, Rarity.RARE).forEach { rarity ->
        val rule = rarities[rarity] ?: fail("pets: rarity $rarity")
        if (rule.lines.size !in 1..2 || rule.floor > rule.ceiling || rule.weight < 0 || (rarity != Rarity.COMMON && rule.floor < 1)) fail("pets: rarity $rarity")
    }
    lines.forEach { line ->
        val def = index.modifier(line.code) ?: fail("pets: line ${line.code}")
        if (def.effects.size != line.values.size || line.values.any { it.size !in 1..2 || it[0] > it.last() } || line.weight <= 0) fail("pets: line ${line.code}")
        if ((line.kind == PetKind.HELPER) != (line.focus != null)) fail("pets: line ${line.code} focus")
    }
    if (lines.groupBy { it.kind to it.focus }.values.any { pool -> pool.map { it.code }.toSet().size != pool.size }) fail("pets: a line twice in a pool")
    val most = rarities.values.maxOf { it.ceiling }
    species.forEach { kind ->
        if (kind.biome !in biomes || kind.weight <= 0) fail("pets: species ${kind.code}")
        when (kind.kind) {
            PetKind.COMBAT -> if (kind.role == null || kind.element !in ELEMENTS || kind.focus != null) fail("pets: species ${kind.code}")
            PetKind.HELPER -> if (kind.focus == null || kind.role != null) fail("pets: species ${kind.code}")
        }
        if (lines.count { it.kind == kind.kind && (kind.kind == PetKind.COMBAT || it.focus == kind.focus) } < most) fail("pets: pool of ${kind.code}")
    }
    biomes.forEach { biome -> PetKind.entries.forEach { kind -> if (species.none { it.biome == biome && it.kind == kind }) fail("pets: no $kind of $biome") } }
    incubator.validate()
    IncubatorRules.STATS.forEach { if (it !in index.stats) fail("pets: incubator stat $it") }
    if (eggChance.rare !in 0.0..1.0 || eggChance.boss !in 0.0..1.0 || lineGrowth < 0 || experienceShare < 0 || drawFire !in 0.0..1.0) fail("pets: rule")
}

private val ELEMENTS = setOf("PHYSICAL", "FIRE", "COLD", "LIGHTNING", "CHAOS")

package com.sperance.exileforge.rules.content

import com.sperance.exileforge.rules.fail
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

/** Что делает сфера питомца - набор как у вещей и рост уровня. */
@Serializable
enum class PetOrbAction { UPGRADE, REROLL, AUGMENT, DIVINE, ANNUL, GROWTH }

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
)

@Serializable
data class PetEggChance(val rare: Double = 0.02, val boss: Double = 0.2)

/** Копия питомца в зверинце: вид, редкость, опыт и уровень, строки долями ролла. */
@Serializable
data class Pet(
    val id: String,
    val species: String,
    val rarity: Rarity = Rarity.COMMON,
    @SerialName("xp") val experience: Double = 0.0,
    val level: Int = 1,
    val lines: List<PetLine> = emptyList(),
)

/** Строка питомца: модификатор и доли ролла по его эффектам. */
@Serializable
data class PetLine(val code: String, @SerialName("p") val shares: List<Double>)

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
    if (eggChance.rare !in 0.0..1.0 || eggChance.boss !in 0.0..1.0 || lineGrowth < 0 || experienceShare < 0 || drawFire !in 0.0..1.0) fail("pets: rule")
}

private val ELEMENTS = setOf("PHYSICAL", "FIRE", "COLD", "LIGHTNING", "CHAOS")

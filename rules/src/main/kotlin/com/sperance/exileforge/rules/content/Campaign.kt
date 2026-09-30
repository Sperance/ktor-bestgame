package com.sperance.exileforge.rules.content

import kotlinx.serialization.Serializable

/** Редкость монстра: обычный, магический, редкий; `UNIQUE` - только босс. */
@Serializable
enum class MonsterRarity {
    NORMAL, MAGIC, RARE, UNIQUE;

    companion object {
        fun of(name: String): MonsterRarity? = entries.firstOrNull { it.name == name }
    }
}

/** Ряд монстра в бою: ближний - первый, дальний - второй и бьёт с первой секунды. */
@Serializable
enum class MonsterRange { MELEE, RANGED }

/**
 * Правило редкости монстра. Вес - в таблице `rarity:monster`; здесь - сколько модификаторов
 * несёт, во сколько раз сильнее их значения ([modifierPower]), «больше» ко всем растущим
 * характеристикам ([statScale]), множители добычи и опыта, и закреплённые строки [lines].
 */
@Serializable
data class RarityRule(
    val rarity: MonsterRarity,
    val modifiers: List<Int> = listOf(0, 0),
    val statScale: Double = 0.0,
    val modifierPower: Double = 1.0,
    val lines: List<Line> = emptyList(),
    val quantity: Double = 1.0,
    val rarityBonus: Double = 0.0,
    val experience: Double = 1.0,
)

@Serializable
data class AilmentRule(
    val ailment: String, val type: String, val chance: Double, val magnitude: Double = 0.0, val duration: Double,
    val threshold: Double = 0.0, val stacks: Boolean = false, val heroChance: Double? = null,
)

@Serializable data class UnarmedRule(val damage: Double, val speed: Double)
@Serializable data class CriticalRule(val chance: Double, val multiplier: Double)
@Serializable data class ArmourRule(val factor: Double)
@Serializable data class EvasionRule(val base: Double, val perLevel: Double)
@Serializable data class StunRule(val share: Double, val duration: Double)
@Serializable data class ShieldRule(val rechargeDelay: Double, val rechargePerSecond: Double)
@Serializable data class RetreatRule(val delay: Double)
@Serializable data class DeathRule(val fromLevel: Int, val experienceShare: Double)
@Serializable data class LoneWolfRule(val dealt: Double = 10.0, val taken: Double = 10.0)
@Serializable data class ManaRule(val regen: Double = 3.0)
@Serializable data class FlaskRule(val perKill: Map<MonsterRarity, Double> = mapOf(MonsterRarity.NORMAL to 1.0, MonsterRarity.MAGIC to 2.0, MonsterRarity.RARE to 3.0, MonsterRarity.UNIQUE to 5.0))

/**
 * Предел характеристики (сервер 1.11.0), устроенный как у сопротивлений: [base] без поднятий, строки [raise]
 * поднимают его, но не выше жёсткого [hard].
 */
@Serializable
data class Ceiling(val base: Double, val hard: Double, val raise: String) {
    fun at(raised: Double): Double = (base + raised).coerceIn(0.0, hard)
}

/** Пределы бойца: шанс блока, шанс уклонения, снижение физического урона (бронёй и строками) и шанс крита. */
@Serializable
data class Ceilings(val block: Ceiling, val evasion: Ceiling, val physical: Ceiling, val critical: Ceiling) {
    val all: List<Ceiling> get() = listOf(block, evasion, physical, critical)
}

/** Правила боя: числа, по которым клиент считает автобой. */
@Serializable
data class CombatRules(
    val variance: Double,
    val resistCap: Double,
    val ceilings: Ceilings,
    val unarmed: UnarmedRule,
    val critical: CriticalRule,
    val armour: ArmourRule,
    val evasion: EvasionRule,
    val stun: StunRule,
    val shield: ShieldRule,
    val retreat: RetreatRule,
    val death: DeathRule,
    val ailments: List<AilmentRule>,
    val resistHardCap: Double = 90.0,
    val ailmentDurationCap: Double = 75.0,
    val loneWolf: LoneWolfRule = LoneWolfRule(),
    val mana: ManaRule = ManaRule(),
    /** Штраф сопротивлений героя по актам (1.16.0): по месту региона зоны, на карте - последний. */
    val resistPenalty: List<Double> = emptyList(),
    val flasks: FlaskRule = FlaskRule(),
    /**
     * Сколько процентов перезарядки активного умения героя ещё идёт в начале боя (1.8.0): стая не падает
     * от залпа всех слотов в первый же кадр. Умения с условием «начало боя» готовы сразу.
     */
    val opening: Double = 0.0,
    /** Потолок «быстрой подготовки» (3.13.0 клиента): на сколько процентов она укорачивает подготовку умения в начале боя. */
    val preparationCap: Double = 75.0,
    /** Меткость против уклонения (1.34.0): заменяет прежнюю формулу [evasion]. */
    val accuracy: AccuracyRule = AccuracyRule(),
    val buffs: BuffRules = BuffRules(),
    val defence: DefenceRule = DefenceRule(),
) {
    /**
     * Какая доля перезарядки умения идёт в начале боя: подготовка умения на его уровне (или [opening], если своей нет),
     * укороченная быстрой подготовкой героя [quickness] в пределах [preparationCap].
     */
    fun preparation(skill: SkillDefinition, level: Int, quickness: Double): Double =
        (skill.prepare?.at(level) ?: opening).coerceIn(0.0, 100.0) / 100 * (1 - quickness.coerceIn(0.0, preparationCap) / 100)
}

/**
 * Монстр на первом уровне: характеристики растут по `growth`, [loot] - тег таблицы добычи, [fixed] -
 * сигнатурные модификаторы босса, [tables] - таблицы его собственных уникалок.
 */
@Serializable
data class Monster(
    val code: String,
    val form: String,
    val experience: Double,
    val loot: String,
    val stats: Map<String, Double>,
    val behaviour: BehaviourRule? = null,
    val boss: Boolean = false,
    val fixed: List<String> = emptyList(),
    val tables: List<String> = emptyList(),
    val corrupted: Boolean = false,
    val range: MonsterRange? = null,
    val skills: List<String> = emptyList(),
)

@Serializable data class ServiceRule(val summonPerLevel: Long)
@Serializable data class FountainRule(val count: List<Int> = listOf(0, 2), val heal: Double = 30.0)

/** Карты-предметы: шансы падения, риск за вредные строки; веса редкости - таблица `rarity:map`. */
@Serializable
data class MapRule(
    val dropChance: Double,
    val bossChance: Double,
    val nextChance: Double,
    val risk: Map<String, Double>,
    val rarityBonus: Map<Rarity, Double> = emptyMap(),
    val rarities: String = "rarity:map",
    /** Уникалка с босса карты (1.19.0): шанс и собственный пул карт. */
    val uniqueChance: Double = 0.0, val uniqueTables: List<String> = emptyList(),
    /**
     * Уникалка Атласа с босса захода по карте-предмету (1.31.0): шанс [atlasUniqueChance] растёт с узлами атласа героя -
     * × (1 + узлы / [atlasUniqueNodes]), то есть вдвое на [atlasUniqueNodes] узлах.
     */
    val atlasUniqueChance: Double = 0.0, val atlasUniqueTables: List<String> = emptyList(), val atlasUniqueNodes: Double = 100.0,
    /** Тиры карт (1.41.0): карты верхних зон несут ступень 1..max; нет раздела - тиров нет. */
    val tiers: MapTierRule? = null,
) {
    /** Шанс уникалки Атласа героя с [nodes] взятыми узлами атласа. */
    fun atlasChance(nodes: Int): Double = atlasUniqueChance * (1 + nodes.coerceAtLeast(0) / atlasUniqueNodes)
}

/**
 * Тиры карт (1.41.0): карта уровня [fromLevel] и выше выпадает со ступенью 1..[max]; каждая ступень прибавляет [effects]
 * (строки карты: сила монстров, размер стай, количество и редкость), упавшая с неё карта с шансом [climb]% - на ступень выше,
 * босс карты с тиром роняет уникалку из [uniqueTables] с шансом [uniqueChance] за ступень.
 */
@Serializable
data class MapTierRule(
    val fromLevel: Int, val max: Int, val effects: Map<String, Double> = emptyMap(), val climb: Double = 30.0,
    val uniqueChance: Double = 0.0, val uniqueTables: List<String> = emptyList(),
)

/**
 * Боссы: страж выхода; [tables] - таблицы мировых уникалок, [modifiers] - таблицы их строк; [goldShare] и
 * [orbShare] - доля золота и ожидаемого числа сфер с его таблицы добычи (1.2.0: босс платил слишком щедро).
 */
@Serializable
data class BossRule(
    val respawnHours: Double, val uniqueChance: Double, val ownUniqueChance: Double, val behaviour: BehaviourRule,
    val tables: List<String> = emptyList(), val modifiers: List<String> = listOf("boss"), val rolls: List<Int> = listOf(1, 2), val tierReach: Int = 5,
    val goldShare: Double = 1.0, val orbShare: Double = 1.0,
    /** Стражи зон ниже [earlyUntil] катают [earlyRolls] строк вместо [rolls] (1.8.0): первые акты - без лотереи модов. */
    val earlyRolls: List<Int> = rolls, val earlyUntil: Int = 0,
    /** Потолок шанса блока стража со всеми строками (1.8.0), ниже общего [CombatRules.blockCap]. */
    val blockCap: Double = 100.0,
    /** Мифическая вещь с босса карты (1.18.0): шанс и таблицы. */
    val mythicChance: Double = 0.0, val mythicTables: List<String> = emptyList(),
) {
    fun rollsAt(level: Int): List<Int> = if (level < earlyUntil) earlyRolls else rolls
}

@Serializable
data class CorruptionRule(
    val chance: Double = 0.0, val uniqueChance: Double = 0.0, val tables: List<String> = emptyList(),
    /** Мифическая вещь со стража Ваал-зоны (1.18.0). */
    val mythicChance: Double = 0.0, val mythicTables: List<String> = emptyList(),
)

/** Ваал-зона: строки из таблицы [pool], лучшего тира уровня карты и в [power] раз сильнее. */
@Serializable
data class VaalRule(val mods: List<Int> = listOf(3, 8), val power: Double = 1.5, val reward: Double = 1.5, val perMod: Double = 4.0, val pool: String = "vaal:zone")

@Serializable
data class BehaviourRule(
    val type: String, val wanderSpeed: Double, val chaseSpeed: Double, val sight: Double,
    val wanderRadius: Double = 0.0, val wake: Double = 0.0, val giveUp: Double,
) {
    companion object { val types = setOf("WANDER", "PATROL", "AMBUSH", "SLEEP") }
}

@Serializable data class BehaviourTable(val default: BehaviourRule, val forms: Map<String, BehaviourRule> = emptyMap())
@Serializable data class WorldPoint(val x: Int, val y: Int)
@Serializable data class WorldRule(val width: Int, val height: Int)

/**
 * Рост со спадом: до [from] полная степень за уровень, выше - доля [rate], а с каждого излома из [bends]
 * (1.8.0) - своя доля: кривая по актам, под рывки силы героя на новых базах.
 */
@Serializable
data class GrowthTaper(val from: Int = Int.MAX_VALUE, val rate: Double = 1.0, val bends: List<GrowthBend> = emptyList()) {
    private val segments: List<GrowthBend> by lazy { listOf(GrowthBend(from, rate)) + bends.sortedBy { it.from } }

    fun steps(level: Int): Double = (minOf(level, from) - 1) + segments.withIndex().sumOf { (i, segment) ->
        val end = segments.getOrNull(i + 1)?.from ?: Int.MAX_VALUE
        segment.rate * (minOf(level, end) - segment.from).coerceAtLeast(0)
    }
}

/** Излом роста: с уровня [from] доля [rate] полной степени за уровень. */
@Serializable data class GrowthBend(val from: Int, val rate: Double)

/** Зона карты мира: жетон, связи, монстры, таблицы модификаторов монстров ([tables]) и добычи сундуков. */
@Serializable
data class Zone(
    val code: String,
    val biome: String,
    val level: Int,
    val x: Int,
    val y: Int,
    val from: List<String> = emptyList(),
    val finale: Boolean = false,
    val monsters: List<String>,
    val count: List<Int>,
    val light: Double = 1.0,
    val size: Int = 72,
    val chestLoot: String,
    val boss: String,
    val tables: List<String> = emptyList(),
    val corrupted: String,
)

@Serializable data class ChestRule(val count: List<Int>, val refreshHours: Double, val quantity: Double, val rarityBonus: Double)
@Serializable data class Region(val code: String, val label: WorldPoint, val zones: List<Zone>)

/** Бездна: расщелины, волны и копилки; [orbs] - таблица сфер копилки, [tables]/[uniques]/[modifiers] - таблицы. */
@Serializable
data class AbyssRule(
    val chance: Double,
    val minLevel: Int,
    val refreshHours: Double,
    val depth: List<Int>,
    val monsters: List<String>,
    val waves: List<AbyssWave>,
    val hoard: List<AbyssHoard>,
    val orbs: String = "orbs:abyss",
    val uniques: List<String>,
    val tables: List<String>,
    val modifiers: List<String>,
    val rolls: List<Int> = listOf(1, 2),
    val tierReach: Int = 5,
) {
    val leaders: List<String> get() = waves.mapNotNull { it.leader }
}

@Serializable data class AbyssWave(val count: List<Int>, val level: Int = 0, val magic: Double = 0.0, val rare: Double = 0.0, val leader: String? = null)
@Serializable data class AbyssHoard(val items: List<Int>, val rare: Double = 0.0, val orbs: List<Int>, val unique: Double = 0.0, val experience: Double = 0.0)

/** Файл `campaign.json`: мир, монстры, правила редкостей, боя и всего, что происходит в зоне. */
@Serializable
data class CampaignFile(
    val defaults: Map<String, Double> = emptyMap(),
    val growth: Map<String, Double> = emptyMap(),
    val growthTaper: GrowthTaper = GrowthTaper(),
    val rarities: List<RarityRule>,
    val monsters: List<Monster>,
    val world: WorldRule,
    val regions: List<Region>,
    val combat: CombatRules,
    val behaviour: BehaviourTable,
    val chests: ChestRule,
    val bosses: BossRule,
    val services: ServiceRule,
    val maps: MapRule,
    val fountains: FountainRule = FountainRule(),
    val corruption: CorruptionRule = CorruptionRule(),
    val vaal: VaalRule = VaalRule(),
    val rangedForms: Set<String> = emptySet(),
    val abyss: AbyssRule? = null,
    /** Осквернение (1.4.0): пятна на земле зон; нет раздела - нет пятен. */
    val desecration: DesecrationRule? = null,
    /** Испытания (1.47.0): босс-раш и башня; нет раздела - нет испытаний. */
    val trials: TrialRules? = null,
    /** Таблица весов редкостей монстров. */
    val rarityTable: String = "rarity:monster",
) {
    val zones: List<Zone> get() = regions.flatMap { it.zones }

    fun rarity(rarity: MonsterRarity): RarityRule = rarities.first { it.rarity == rarity }

    fun range(monster: Monster): MonsterRange = monster.range ?: if (monster.form in rangedForms) MonsterRange.RANGED else MonsterRange.MELEE

    fun behaviourOf(monster: Monster): BehaviourRule = monster.behaviour ?: behaviour.forms[monster.form] ?: behaviour.default

    /** Насколько меньше сопротивления у героя в зоне [zoneCode]: по акту, на карте - по последнему (1.16.0). */
    fun resistPenalty(zoneCode: String, onMap: Boolean): Double {
        val steps = combat.resistPenalty.ifEmpty { return 0.0 }
        if (onMap) return steps.last()
        val act = regions.indexOfFirst { region -> region.zones.any { it.code == zoneCode } }
        return if (act < 0) 0.0 else steps.getOrElse(act) { steps.last() }
    }
}

/** Связи зон карты мира: зона открыта, когда пройдена хоть одна из тех, что ведут к ней; стартовая - всегда. */
class WorldGraph(zones: List<Zone>) {
    private val codes: List<String> = zones.map { it.code }
    private val from: Map<String, List<String>> = zones.associate { it.code to it.from }
    private val to: Map<String, List<String>> = zones.flatMap { zone -> zone.from.map { it to zone.code } }.groupBy({ it.first }, { it.second })

    fun next(code: String): List<String> = to[code].orEmpty()

    fun unlocked(passed: Collection<String>): List<String> {
        val done = passed.toHashSet()
        return codes.filter { code -> from.getValue(code).let { it.isEmpty() || it.any(done::contains) } }
    }

    fun unreachable(): Set<String> {
        val seen = codes.filterTo(HashSet()) { from.getValue(it).isEmpty() }
        val queue = ArrayDeque(seen)
        while (queue.isNotEmpty()) next(queue.removeFirst()).forEach { if (seen.add(it)) queue.addLast(it) }
        return codes.toSet() - seen
    }
}

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
/**
 * Крит (1.56.0): шанс и множитель атак и свои - у заклинаний ([spellChance], [spellMultiplier]; без них - как у атак).
 * [sheetBase] - база листа героя: увеличения шанса и прибавки к множителю ложатся на неё, а не на ноль.
 * Урон крита (1.57.0) - [damage] процентов прибавки крита сверх обычного удара, у героя и у монстра ([fighterBase]):
 * «увеличение урона критических ударов» растит эту долю, 0 - крит бьёт как обычный удар.
 */
@Serializable
data class CriticalRule(
    val chance: Double, val multiplier: Double, val spellChance: Double = chance, val spellMultiplier: Double = multiplier, val damage: Double = 100.0,
) {
    /** База каждого бойца: урон крита, на который ложатся его увеличения. */
    val fighterBase: Map<String, Double> get() = mapOf(CoreStat.CRITICAL_DAMAGE.code to damage)

    val sheetBase: Map<String, Double> get() = fighterBase + mapOf(
        CoreStat.CRITICAL_CHANCE.code to chance, CoreStat.CRITICAL_MULTIPLIER.code to multiplier,
        CoreStat.SPELL_CRITICAL_CHANCE.code to spellChance, CoreStat.SPELL_CRITICAL_MULTIPLIER.code to spellMultiplier,
    )

    /**
     * Множитель крита в процентах с уроном крита: прибавка [multiplier] сверх 100 растёт на [critDamage]/100 -
     * 150% при уроне крита 140 бьют на 170%. Урона крита нет в листе - его база [damage].
     */
    fun effective(multiplier: Double, critDamage: Double?): Double =
        100 + (multiplier.coerceAtLeast(100.0) - 100) * (critDamage ?: damage).coerceAtLeast(0.0) / 100
}
@Serializable data class ArmourRule(val factor: Double)
@Serializable data class EvasionRule(val base: Double, val perLevel: Double)
@Serializable data class StunRule(val share: Double, val duration: Double)

/**
 * Шкалы накопления (1.73.0, как в PoE2): удар копит оглушение (физический урон целиком, прочий - долей [stunOther], крит -
 * в [stunCrit] раз), заморозку (холод) и электрошок (молния) долей запаса цели - её здоровья на множитель [pools] её
 * редкости ([heroPool] у героя и питомца; оглушению ещё порог). Полная шкала срабатывает и пустеет, на [immunity] секунд
 * цель к ней глуха; без новых ударов [decayDelay] секунд шкалы тают на [decayPerSecond] в секунду.
 */
@Serializable
data class BuildupRule(
    val pools: Map<MonsterRarity, Double> = mapOf(MonsterRarity.NORMAL to .5, MonsterRarity.MAGIC to .7, MonsterRarity.RARE to 1.2, MonsterRarity.UNIQUE to 2.5),
    val heroPool: Double = 1.0,
    val stunOther: Double = .5,
    val stunCrit: Double = 1.5,
    val decayDelay: Double = 2.0,
    val decayPerSecond: Double = .25,
    val immunity: Double = 4.0,
    val stun: BuildupEffect = BuildupEffect(1.5, 1.0, 25.0),
    val freeze: BuildupEffect = BuildupEffect(2.0, 1.2, 50.0),
    val electrocute: BuildupEffect = BuildupEffect(1.5, 1.0, 30.0),
)

/**
 * Что даёт полная шкала: цель не действует [duration] секунд ([bossDuration] - босс) и получает больше на [bonus]
 * процентов: оглушённая - любого урона, замороженная - первым ударом, что разбивает лёд, под электрошоком - молнии.
 */
@Serializable
data class BuildupEffect(val duration: Double, val bossDuration: Double, val bonus: Double)
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
    /** Подкрепление стаи (1.69.0): сколько секунд место павшего пустует, прежде чем следующий из очереди встанет на него. */
    val reinforceDelay: Double = 0.0,
    /** Шкалы накопления (1.73.0): есть - оглушение и заморозка копятся шкалой вместо порога и шанса; нет - по-старому. */
    val buildup: BuildupRule? = null,
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
    val skills: List<String> = emptyList(),
    /** Своё свойство типа (1.69.0) - код из `traits.list`; к нему добавляется свойство формы. */
    val trait: String? = null,
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
    /**
     * Доля риска, что идёт в редкость (1.71.0): у карт больше нет строк «больше добычи» - количество и редкость дают только
     * вредные строки, количество полным риском, редкость - этой долей.
     */
    val riskRarity: Double = .6,
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
    /** Карты, захваченные влиянием (1.50.0). */
    val influence: MapInfluenceRule = MapInfluenceRule(),
    /** Разброс уровня монстров захода по карте (1.69.0): каждый не-босс - уровень зоны ± [levelSpread], равномерно. */
    val levelSpread: Int = 0,
) {
    /** Шанс уникалки Атласа героя с [nodes] взятыми узлами атласа. */
    fun atlasChance(nodes: Int): Double = atlasUniqueChance * (1 + nodes.coerceAtLeast(0) / atlasUniqueNodes)
}

/**
 * Захваченная карта (1.50.0): упавшая карта с шансом [chance]% захвачена одним из [kinds] (или сферой влияния); на ней
 * волшебная и редкая вещь с шансом [items]% несёт её влияние, монстры сильнее на [power]% здоровья и урона (риск карты
 * платит за это), а босс всегда роняет редкую вещь её влияния.
 */
@Serializable
data class MapInfluenceRule(
    val chance: Double = 8.0,
    val items: Double = 15.0,
    val power: Double = 20.0,
    val kinds: List<Influence> = listOf(Influence.SHAPER, Influence.ELDER),
    /** Расщелин Бездны сверх окна на карте, захваченной Бездной (1.65.0: Бездну карте даёт только её сфера). */
    val abyssCracks: Int = 2,
) {
    /** Захватывает ли карту влияние [influence]: случайные захваты [kinds] и Бездна от сферы. */
    fun accepts(influence: Influence): Boolean = influence in kinds || influence == Influence.ABYSS

    /** Влияния случайного захвата при узлах атласа [atlas]: «только Создатель» или «только Древний»; оба - оба. */
    fun pool(atlas: Map<String, Double>): List<Influence> {
        val shaper = (atlas[AtlasStat.INFLUENCE_SHAPER.code] ?: 0.0) > 0
        val elder = (atlas[AtlasStat.INFLUENCE_ELDER.code] ?: 0.0) > 0
        return when {
            shaper && !elder -> kinds.filter { it == Influence.SHAPER }
            elder && !shaper -> kinds.filter { it == Influence.ELDER }
            else -> kinds
        }.ifEmpty { kinds }
    }
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
    val abyss: AbyssRule? = null,
    /** Испытания (1.47.0): босс-раш и башня; нет раздела - нет испытаний. */
    val trials: TrialRules? = null,
    /** Свойства монстров (1.69.0). */
    val traits: TraitRules = TraitRules(),
    /** Таблица весов редкостей монстров. */
    val rarityTable: String = "rarity:monster",
    /** Сундуки-добыча (1.71.0): предметы сумки, что падают редко и открываются у героя; порядок списка - порядок бросков. */
    val lootChests: List<LootChest> = emptyList(),
) {
    val zones: List<Zone> get() = regions.flatMap { it.zones }

    fun rarity(rarity: MonsterRarity): RarityRule = rarities.first { it.rarity == rarity }

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

/**
 * Сундук-добыча (1.71.0, тиры с 1.72.0): вид [code] (`CHEST_WOODEN`), предмет сумки - его тир по уровню источника, по
 * [LEVELS_PER_TIER] уровней (`CHEST_WOODEN_T3` - уровни 21-30), открывается на верхнем уровне тира. Торгуется только на
 * аукционе, сфер не берёт.
 *
 * Где падает: с монстра шансом [dropChance] (с множителем количества его редкости), с босса зоны - [bossChance], с босса
 * финала региона ещё и [finaleChance], из награды испытания - [trialChance] и [trialPerLevel] за каждый уровень арены, не
 * выше [trialMax]; не ниже уровня [minLevel].
 *
 * Что внутри: таблица добычи своего тира ([tableOf]) [rolls] раз, шансы и золото как записаны (редкость [rarity] решает
 * лишь уровень вещей), с шансом
 * [jackpotChance] золото в [jackpot] раз больше; вещи [gear]; уникалка с шансом [uniqueChance] из [uniqueTables]
 * ([uniqueFlat] - все поровну, [classUnique] - только класса открывшего); карты [maps]; стопки [items] (код - [от, до]).
 * [slots] - каким слотам быть вещам и уникалке, пусто - любым.
 */
@Serializable
data class LootChest(
    val code: String,
    val table: String? = null,
    val rolls: Int = 1,
    val rarity: MonsterRarity = MonsterRarity.RARE,
    val jackpotChance: Double = 0.0,
    val jackpot: Double = 1.0,
    val gear: List<ChestGear> = emptyList(),
    val slots: List<Slot> = emptyList(),
    val uniqueChance: Double = 0.0,
    val uniqueTables: List<String> = emptyList(),
    val uniqueFlat: Boolean = false,
    val classUnique: Boolean = false,
    val maps: ChestMaps? = null,
    val items: Map<String, List<Long>> = emptyMap(),
    val dropChance: Double = 0.0,
    val bossChance: Double = 0.0,
    val finaleChance: Double = 0.0,
    val trialChance: Double = 0.0,
    val trialPerLevel: Double = 0.0,
    val trialMax: Double = 1.0,
    val minLevel: Int = 1,
) {
    /** Тиры, в которых сундук бывает: от тира [minLevel] до последнего. */
    val tiers: IntRange get() = tierOf(minLevel)..MAX_TIER

    /** Таблица добычи тира [tier]: у каждого тира своя (`loot:LCHEST_ORBS_T3`), сферы растут с землями. */
    fun tableOf(tier: Int): String? = table?.let { "${it}_T$tier" }

    /** Код предмета сундука тира [tier]. */
    fun tierCode(tier: Int): String = "${code}_T$tier"

    companion object {
        const val LEVELS_PER_TIER = 10
        const val MAX_TIER = 10
        fun tierOf(level: Int): Int = ((level.coerceAtLeast(1) - 1) / LEVELS_PER_TIER + 1).coerceAtMost(MAX_TIER)
        /** Верхний уровень тира: на нём сундук открывается. */
        fun levelOf(tier: Int): Int = tier * LEVELS_PER_TIER
    }
}

/**
 * Вещи сундука: [count] штук, каждая с шансом [chance], из таблиц [tables] редкости [rarity] (волшебная станет редкой с
 * шансом [rareChance]); [influenced] - с влиянием, [corrupted] - осквернённая сферой Ваал, [classFit] - под атрибуты класса
 * открывшего (оружие и броня его требований).
 */
@Serializable
data class ChestGear(
    val count: Int = 1,
    val chance: Double = 1.0,
    val tables: List<String> = listOf("drop"),
    val rarity: Rarity = Rarity.RARE,
    val rareChance: Double = 0.0,
    val influenced: Boolean = false,
    val corrupted: Boolean = false,
    val classFit: Boolean = false,
    val slots: List<Slot> = emptyList(),
)

/** Карты сундука: [count] штук зон в [spread] тирах сундука от его тира, редкая - с шансом [rareChance]. */
@Serializable
data class ChestMaps(val count: Int = 1, val spread: Int = 2, val rareChance: Double = 0.0)

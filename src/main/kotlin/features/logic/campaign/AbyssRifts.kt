package features.logic.campaign

import application.enums.EnumRarity
import application.enums.EnumStatStock
import features.logic.atlas.AtlasBonuses
import kotlinx.serialization.Serializable
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.random.Random

/**
 * Бездна (0.72.0): расщелины в зонах от [minLevel] - в окне зоны с шансом [chance] процентов, как
 * кристаллы, - а в каждой спуск по ступеням: волна за волной, после каждой герой забирает копилку
 * или идёт глубже. Ступень - это [waves] и [hoard] под одним номером; сколько ступеней ведёт
 * расщелина, катится от `depth[0]` до `depth[1]`. Монстры [monsters] - свои, у вожака ступени свой
 * пул [modifierPools]; копилка тянет вещи из [equipmentPools] с влиянием Бездны, сферы - по весам
 * [orbs], уникалку - из [uniquePools].
 */
@Serializable
data class AbyssRule(
    val chance: Double,
    val minLevel: Int,
    val refreshHours: Double,
    val depth: List<Int>,
    val monsters: List<String>,
    val waves: List<AbyssWave>,
    val hoard: List<AbyssHoard>,
    val orbs: Map<String, Int>,
    val uniquePools: List<String>,
    val equipmentPools: List<String>,
    val modifierPools: List<String>,
    val rolls: List<Int> = listOf(1, 2),
    val tierReach: Int = 5,
) {
    val leaders: List<String> get() = waves.mapNotNull { it.leader }
}

/**
 * Волна ступени: от `count[0]` до `count[1]` монстров на [level] уровней выше зоны, [magic] и [rare] -
 * доли волшебных и редких в процентах; [leader] - вожак, что встаёт с волной.
 */
@Serializable
data class AbyssWave(val count: List<Int>, val level: Int = 0, val magic: Double = 0.0, val rare: Double = 0.0, val leader: String? = null)

/**
 * Копилка к концу ступени, всё вместе с прежними: вещей и сфер - от первого числа до второго, [rare] -
 * доля редких вещей в процентах, [unique] - шанс уникалки Бездны, [experience] - опыт в обычных
 * монстрах Бездны уровня зоны.
 */
@Serializable
data class AbyssHoard(val items: List<Int>, val rare: Double = 0.0, val orbs: List<Int>, val unique: Double = 0.0, val experience: Double = 0.0)

/** Копилка, какой её видит герой: с картой, атласом и силами уникалок; опыт - уже в очках. */
@Serializable
data class AbyssHoardView(val items: List<Int>, val rare: Double, val orbs: List<Int>, val unique: Double, val experience: Double)

/** Ступень для клиента: волна, её [level] и монстры на нём, вожак и копилка к её концу. */
@Serializable
data class AbyssDepth(val level: Int, val count: List<Int>, val magic: Double, val rare: Double, val monsters: List<CampaignMonster>,
                      val leader: CampaignBoss? = null, val hoard: AbyssHoardView)

/**
 * Бездна зоны на этот заход: [cracks] - докуда ведёт каждая расщелина (с картой), [modifiers] - пул
 * волшебных и редких монстров Бездны на уровне зоны, [keep] - доля копилки в процентах, что уцелеет,
 * если герой падёт.
 */
@Serializable
data class AbyssLaunch(val cracks: List<Int>, val refreshAt: Long, val depths: List<AbyssDepth>, val modifiers: List<MonsterModifier>, val keep: Double)

/** Ступень на уровне зоны: её монстры и вожак, если он встаёт с этой волной. */
class AbyssFloor(val monsters: List<CampaignMonster>, val leader: CampaignBoss?)

/** Бездна на уровне зоны: ступени по порядку и пул модификаторов её монстров. */
class AbyssFloors(val floors: List<AbyssFloor>, val modifiers: List<MonsterModifier>)

/** Окно расщелин зоны у героя: до [refreshAt] стоят те, что остались, - по глубине каждой. */
@Serializable
data class AbyssWindow(val refreshAt: Long = 0, val cracks: List<Int> = emptyList())

/** Спуск, открытый героем: зона и сколько ступеней в нём. Копилка или гибель его закрывают. */
@Serializable
data class AbyssRun(val mapCode: String, val depth: Int)

/** Ответ на открытую расщелину: докуда спуск и какие расщелины в зоне остались. */
@Serializable
data class AbyssOpened(val depth: Int, val cracks: List<Int>, val refreshAt: Long)

/**
 * Прибавки к копилке: множители вещей, сфер и шанса уникалки, пункты к доле редких и опыт одной
 * единицы копилки в очках.
 */
data class HoardBonus(val items: Double = 1.0, val rare: Double = 0.0, val orbs: Double = 1.0, val unique: Double = 1.0, val experience: Double = 0.0)

/** Что легло из копилки: редкости вещей, сферы по кодам, уникалка ли и опыт. */
data class HoardRoll(val items: List<EnumRarity>, val orbs: Map<String, Long>, val unique: Boolean, val experience: Double) {
    companion object { val EMPTY = HoardRoll(emptyList(), emptyMap(), false, 0.0) }
}

/** Бездна - правило сервера, как сундуки и кристаллы. Функции чистые: время и [Random] приходят снаружи. */
object AbyssRifts {

    /**
     * Окно на момент [now]: живое остаётся как есть, истёкшее бросается заново - зона ниже [AbyssRule.minLevel]
     * пуста, иначе расщелина встаёт с шансом правила и атласа, а вторая - с шансом атласа.
     */
    fun window(current: AbyssWindow?, now: Long, rule: AbyssRule, level: Int, random: Random, atlas: AtlasBonuses): AbyssWindow {
        if (current != null && now < current.refreshAt) return current
        val refreshAt = now + (rule.refreshHours * 3_600_000).toLong()
        if (level < rule.minLevel || random.nextDouble() * 100 >= rule.chance + atlas[EnumStatStock.ATLAS_ABYSS_CHANCE]) return AbyssWindow(refreshAt)
        val count = 1 + if (random.nextDouble() * 100 < atlas[EnumStatStock.ATLAS_ABYSS_EXTRA]) 1 else 0
        return AbyssWindow(refreshAt, List(count) { crack(rule, random, atlas) })
    }

    /** Сколько ступеней в новой расщелине: бросок правила и глубина атласа, не глубже волн. */
    fun crack(rule: AbyssRule, random: Random, atlas: AtlasBonuses): Int =
        (random.nextInt(rule.depth[0], rule.depth[1] + 1) + atlas[EnumStatStock.ATLAS_ABYSS_DEPTH].toInt()).coerceIn(1, rule.waves.size)

    /** Докуда ведёт расщелина [crack] на этом заходе: её ступени и строка карты [map], не глубже волн. */
    fun depth(rule: AbyssRule, crack: Int, map: Double): Int = (crack + map.toInt()).coerceIn(1, rule.waves.size)

    /** Копилка ступени [depth] для героя: числа правила с прибавками [bonus]. */
    fun view(rule: AbyssRule, depth: Int, bonus: HoardBonus): AbyssHoardView {
        val hoard = rule.hoard[depth - 1]
        fun range(pair: List<Int>, scale: Double) = listOf(floor(pair[0] * scale).toInt(), ceil(pair[1] * scale).toInt())
        return AbyssHoardView(range(hoard.items, bonus.items), (hoard.rare + bonus.rare).coerceIn(0.0, 100.0), range(hoard.orbs, bonus.orbs),
            (hoard.unique * bonus.unique).coerceIn(0.0, 100.0), Math.round(hoard.experience * bonus.experience).toDouble())
    }

    /**
     * Копилка ступени [depth]: вещи волшебные или редкие по доле редких, сферы по весам правила,
     * уникалка шансом и опыт. [keep] - уцелевшая доля (1 - вся): после гибели каждое число копилки
     * умножается на неё, так что без атласа она сгорает целиком.
     */
    fun roll(rule: AbyssRule, depth: Int, bonus: HoardBonus, keep: Double, random: Random): HoardRoll {
        if (depth < 1 || keep <= 0) return HoardRoll.EMPTY
        val hoard = rule.hoard[depth - 1]
        val share = keep.coerceAtMost(1.0)
        val rare = (hoard.rare + bonus.rare).coerceIn(0.0, 100.0)
        val items = List(times(random.nextInt(hoard.items[0], hoard.items[1] + 1) * bonus.items * share, random)) {
            if (random.nextDouble() * 100 < rare) EnumRarity.RARE else EnumRarity.UNCOMMON
        }
        val orbs = mutableMapOf<String, Long>()
        repeat(times(random.nextInt(hoard.orbs[0], hoard.orbs[1] + 1) * bonus.orbs * share, random)) { orbs.merge(orb(rule, random), 1L, Long::plus) }
        val unique = random.nextDouble() * 100 < hoard.unique * bonus.unique * share
        return HoardRoll(items, orbs, unique, Math.round(hoard.experience * bonus.experience * share).toDouble())
    }

    private fun orb(rule: AbyssRule, random: Random): String {
        var point = random.nextInt(rule.orbs.values.sum())
        return rule.orbs.entries.first { (_, weight) -> point -= weight; point < 0 }.key
    }

    /** Дробное число целиком: целая часть и остаток шансом. */
    private fun times(expected: Double, random: Random): Int {
        val whole = floor(expected).toInt()
        return whole + if (random.nextDouble() < expected - whole) 1 else 0
    }
}

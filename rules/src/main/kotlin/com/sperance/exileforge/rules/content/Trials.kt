package com.sperance.exileforge.rules.content

import com.sperance.exileforge.rules.fail
import com.sperance.exileforge.rules.roll.Streams
import com.sperance.exileforge.rules.run.RunContext
import kotlinx.serialization.Serializable

/** Строка этажа башни (1.47.0): строка карты [stat] действием [op] на [value] - монстрам или герою, как на карте. */
@Serializable
data class TowerMod(val stat: String, val op: Op, val value: Double)

/**
 * Босс-раш (1.47.0): вход - ключ раша [TrialRules.KEY], собранный из [key] фрагментов герба (1.48.0); между боссами герой получает [life]% здоровья и [flaskCharges] зарядов
 * каждой фляги. Сундук в конце: [orbs] сфер таблицы [orbTable] и опыт за каждого убитого босса; полная зачистка - уникалка
 * из собственных таблиц боссов региона (или [uniqueTables]); уложился в [seconds] секунд на босса - ещё [fastItems] редких вещей.
 */
@Serializable
data class RushRule(
    val key: Int = 5,
    val life: Double = 25.0,
    val flaskCharges: Double = 1.0,
    val orbs: List<Int> = listOf(1, 2),
    val orbTable: String = "orbs:abyss",
    val uniqueTables: List<String> = emptyList(),
    val itemTables: List<String> = listOf("drop"),
    val seconds: Double = 45.0,
    val fastItems: Int = 1,
)

/**
 * Бесконечная башня (1.47.0): этаж - волна Бездны; монстры на уровне героя плюс этаж / [levelStep], с [growth]% «больше»
 * здоровья и урона за каждый этаж выше первого. Каждый [hoardEvery]-й этаж - лидер Бездны и клад, растущий на [hoardGrowth]%
 * за каждый прежний клад; каждые [modEvery] этажей - ещё одна строка из [mods] (выбор задан номером десятка, одинаков для всех)
 * и +[modHoard]% к кладу за каждую. Вход - с последнего чекпоинта, кратного [checkpoint].
 */
@Serializable
data class TowerRule(
    val levelStep: Int = 2,
    val growth: Double = 8.0,
    val hoardEvery: Int = 5,
    val hoardGrowth: Double = 15.0,
    val modEvery: Int = 10,
    val modHoard: Double = 10.0,
    val checkpoint: Int = 10,
    val mods: List<TowerMod> = emptyList(),
    /** Последний этаж башни (1.53.0): пройден - испытание закрыто; клад и опыт этажей не растут без конца. */
    val maxFloor: Int = 100,
) {
    /** Уровень монстров этажа [floor] у героя уровня [heroLevel]. */
    fun level(heroLevel: Int, floor: Int): Int = heroLevel + floor / levelStep

    /** «Больше» здоровья и урона монстров этажа, проценты. */
    fun power(floor: Int): Double = growth * (floor - 1).coerceAtLeast(0)

    /** Строки этажа: по одной на каждый пройденный десяток, выбранные его номером. */
    fun mods(floor: Int): List<TowerMod> = if (mods.isEmpty()) emptyList() else (1..floor / modEvery).map { Streams(MOD_SEED).of("towerMod", it).pick(mods) }

    fun hoard(floor: Int): Boolean = floor > 0 && floor % hoardEvery == 0

    /** Во сколько раз клад этажа больше своей глубины Бездны. */
    fun hoardScale(floor: Int): Double = (1 + hoardGrowth / 100 * (floor / hoardEvery - 1).coerceAtLeast(0)) * (1 + modHoard / 100 * mods(floor).size)

    /** С какого этажа начинается вход героя, чей рекорд - [best] пройденных этажей. */
    fun start(best: Int): Int = best.coerceAtLeast(0) / checkpoint * checkpoint + 1

    /** Этаж [floor] у героя уровня [heroLevel]: волна Бездны по номеру (дальше последней - последняя), лидер - на этаже клада. */
    fun floor(abyss: AbyssRule, heroLevel: Int, floor: Int): TowerFloor {
        val leaders = abyss.leaders
        val leader = if (hoard(floor) && leaders.isNotEmpty()) leaders[(floor / hoardEvery - 1) % leaders.size] else null
        return TowerFloor(
            floor,
            level(heroLevel, floor),
            abyss.waves[(floor - 1).coerceIn(0, abyss.waves.lastIndex)].copy(leader = leader),
            power(floor),
            mods(floor),
            hoard(floor),
        )
    }

    /** Глубина Бездны, чей клад лежит на этаже [floor]. */
    fun hoardDepth(abyss: AbyssRule, floor: Int): Int = (floor / hoardEvery).coerceIn(1, abyss.hoard.size)

    private companion object {
        const val MOD_SEED = 0x546F776572L
    }
}

/** Этаж башни: номер, уровень монстров, волна (с лидером на этаже клада), «больше» здоровья и урона, строки этажа и есть ли клад. */
data class TowerFloor(val floor: Int, val level: Int, val wave: AbyssWave, val power: Double, val mods: List<TowerMod>, val hoard: Boolean)

/**
 * Испытания (1.47.0): босс-раш по зачищенному региону и бесконечная башня. Фрагмент герба [CREST] и печать башни [SEAL]
 * падают с любой награды захода - с шансом [crest] и [seal] процентов, умноженным на множитель количества редкости источника.
 * Очко атласа - за первую зачистку региона в раше и за каждый [atlasFloors]-й этаж башни.
 */
@Serializable
data class TrialRules(
    val crest: Double = 0.8,
    val seal: Double = 0.8,
    val atlasFloors: Int = 25,
    val rush: RushRule = RushRule(),
    val tower: TowerRule = TowerRule(),
) {
    fun validate(index: ContentIndex) {
        tower.mods.forEach { if (it.stat !in index.stats) fail("trials: tower mod ${it.stat} is no stat") }
        if (rush.key < 1 || tower.levelStep < 1 || tower.hoardEvery < 1 || tower.modEvery < 1 || tower.checkpoint < 1 || atlasFloors < 1) fail("trials: steps must be positive")
        if (rush.orbs.size != 2 || rush.orbs[0] < 0 || rush.orbs[0] > rush.orbs[1]) fail("trials: rush orbs")
        listOf(CREST, KEY, SEAL).forEach { if (index.items[it] == null) fail("trials: item $it missing") }
    }

    companion object {
        const val CREST = "CREST_FRAGMENT"

        /** Ключ раша (1.48.0): собирается из фрагментов герба, открывает раш любого зачищенного региона. */
        const val KEY = "RUSH_KEY"
        const val SEAL = "TOWER_SEAL"

        /** Арена испытания на уровне [level]: зона кампании не выше него (иначе первая) - её биом и таблицы, но уровень испытания. */
        fun arena(campaign: CampaignFile, level: Int): Zone = (campaign.zones.lastOrNull { it.level <= level } ?: campaign.zones.first()).copy(level = level)
    }
}

/** Вид испытания. */
@Serializable
enum class TrialKind { RUSH, TOWER }

/**
 * Открытое испытание героя (1.47.0): [seed] боёв клиента, [region] раша или [floor] - этаж башни, который сейчас
 * идёт (с 1), [killed] боссов раша, [hoards] кладов башни, когда оно начато.
 * [entry] (1.68.0) - этаж входа: темп захода считается от него, а не от рекорда, растущего по ходу.
 * [context] (1.68.0) - контекст героя, замороженный на вход: награды всего испытания катятся по нему, а не по листу на момент журнала.
 */
@Serializable
data class TrialRun(
    val id: String,
    val kind: TrialKind,
    val seed: Long,
    val heroLevel: Int,
    val startedAt: Long,
    val region: String = "",
    val floor: Int = 1,
    val killed: Int = 0,
    val hoards: Int = 0,
    val applied: Int = 0,
    val entry: Int = floor,
    val context: RunContext? = null,
)

/** Испытания героя: открытое [run], рекорд башни [towerBest], лучшее время раша по региону (секунды), зачищенные в раше регионы. */
@Serializable
data class TrialProgress(
    val run: TrialRun? = null,
    val towerBest: Int = 0,
    val rushBest: Map<String, Long> = emptyMap(),
    val rushCleared: List<String> = emptyList(),
)

/** Раш региона [region]: его боссы по порядку зон. */
class RushPlan(val region: Region) {
    val zones: List<Zone> get() = region.zones
    val size: Int get() = region.zones.size

    companion object {
        /** Регион открыт для раша, когда все его зоны зачищены героем. */
        fun open(region: Region, cleared: Collection<String>): Boolean = region.zones.all { it.code in cleared }
    }
}

/** События журнала испытания: босс раша пал, этаж башни пройден, испытание кончилось - гибелью ([TrialEvent.fallen]) или уходом. */
@Serializable
enum class TrialEventKind {
    BOSS,
    FLOOR,
    END,

    /** Бой окончен (1.49.0): итог [TrialEvent.fight] - только в статистику героя. */
    FIGHT,
}

/** Событие испытания [n]: [index] - номер босса раша или этаж башни. */
@Serializable
data class TrialEvent(val n: Int, val kind: TrialEventKind, val index: Int = 0, val fallen: Boolean = false, val fight: FightTally? = null)

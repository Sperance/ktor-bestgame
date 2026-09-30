package com.sperance.exileforge.rules.content

import com.sperance.exileforge.rules.fail
import kotlinx.serialization.Serializable

/**
 * Счётчики летописи героя (1.3.0): что сервер считает по ходу игры - единственный источник для достижений и
 * заданий (1.42.0). Сумма копится ([add]), рекорд держит наибольшее ([MAX]); выводимые [DERIVED] - уровень, зоны,
 * узлы атласа и дерева - хранятся рекордом: откат узлов их не уменьшает.
 */
object Counter {
    const val KILLS = "KILLS"
    const val KILLS_MAGIC = "KILLS_MAGIC"
    const val KILLS_RARE = "KILLS_RARE"
    const val BOSSES = "BOSSES"
    const val VAAL_GUARDIANS = "VAAL_GUARDIANS"
    const val DEATHS = "DEATHS"
    const val ABYSS_DEPTH = "ABYSS_DEPTH"
    const val RUNS = "RUNS"
    const val CHESTS = "CHESTS"
    const val CRYSTALS = "CRYSTALS"
    const val ITEMS_MAGIC = "ITEMS_MAGIC"
    const val ITEMS_RARE = "ITEMS_RARE"
    const val UNIQUES = "UNIQUES"
    const val GOLD_EARNED = "GOLD_EARNED"
    const val GOLD_SPENT = "GOLD_SPENT"
    const val ITEMS_SOLD = "ITEMS_SOLD"
    const val AUCTION_SOLD = "AUCTION_SOLD"
    const val AUCTION_BOUGHT = "AUCTION_BOUGHT"
    const val ORBS_USED = "ORBS_USED"
    const val ESSENCES_USED = "ESSENCES_USED"
    const val MIRRORS = "MIRRORS"
    const val CRAFT_CYCLES = "CRAFT_CYCLES"
    const val CRAFT_MADE = "CRAFT_MADE"
    const val LEVEL = "LEVEL"
    const val ZONES = "ZONES"
    const val ATLAS = "ATLAS"
    /** Взятые узлы дерева: выводимый, в летописи не показывается - только цель заданий. */
    const val TREE = "TREE"

    /** Выводимые из состояния героя: хранятся рекордом, см. [derived]. */
    val DERIVED = setOf(LEVEL, ZONES, ATLAS, TREE)

    /** Рекорды: пишется наибольшее значение, а не сумма. */
    val MAX = setOf(ABYSS_DEPTH) + DERIVED

    /** Все счётчики по разделам летописи, в порядке показа. */
    val SECTIONS: Map<String, List<String>> = linkedMapOf(
        "COMBAT" to listOf(KILLS, KILLS_MAGIC, KILLS_RARE, BOSSES, VAAL_GUARDIANS, DEATHS, ABYSS_DEPTH, RUNS, CHESTS, CRYSTALS),
        "LOOT" to listOf(ITEMS_MAGIC, ITEMS_RARE, UNIQUES, GOLD_EARNED, GOLD_SPENT, ITEMS_SOLD, AUCTION_SOLD, AUCTION_BOUGHT),
        "CRAFT" to listOf(ORBS_USED, ESSENCES_USED, MIRRORS, CRAFT_CYCLES, CRAFT_MADE),
        "PROGRESS" to listOf(LEVEL, ZONES, ATLAS),
    )
    val ALL: Set<String> = SECTIONS.values.flatten().toSet() + DERIVED

    /** Нынешние значения выводимых счётчиков по состоянию героя. */
    fun derived(level: Int, zones: Int, atlas: Int, tree: Int): Map<String, Long> =
        mapOf(LEVEL to level.toLong(), ZONES to zones.toLong(), ATLAS to atlas.toLong(), TREE to tree.toLong())

    /** Вписывает нынешние [derived] в [counters] рекордом. */
    fun record(counters: MutableMap<String, Long>, derived: Map<String, Long>) = derived.forEach { (counter, value) -> add(counters, counter, value) }

    /** Летопись целиком: хранимые счётчики, выводимые - наибольшее из рекорда и нынешнего значения. */
    fun values(counters: Map<String, Long>, derived: Map<String, Long>): Map<String, Long> =
        counters + derived.mapValues { (counter, value) -> maxOf(value, counters[counter] ?: 0L) }

    /** Прибавить к счётчику в [counters]: сумма или рекорд - как велит его вид. */
    fun add(counters: MutableMap<String, Long>, counter: String, amount: Long = 1) {
        if (amount <= 0) return
        if (counter in MAX) counters[counter] = maxOf(counters[counter] ?: 0L, amount) else counters.merge(counter, amount, Long::plus)
    }
}

/**
 * Достижение: счётчик и его ступени - бронза, серебро, золото ([tiers] по возрастанию) или одна
 * ступень у разового. Золото или единственная ступень открывает титул [title]; титул - только
 * подпись у имени, без бонусов.
 */
@Serializable
data class Achievement(val code: String, val counter: String, val tiers: List<Long>, val title: String = "") {
    /** Сколько ступеней взято при значении [value]: от нуля до `tiers.size`. */
    fun reached(value: Long): Int = tiers.count { value >= it }
    fun complete(value: Long): Boolean = reached(value) == tiers.size
}

/** Файл `achievements.json`. */
@Serializable
data class AchievementsFile(val achievements: List<Achievement> = emptyList()) {
    val byCode: Map<String, Achievement> by lazy { achievements.associateBy { it.code } }

    /** Титулы, что открыли значения [values]: золото или единственная ступень достижения. */
    fun titles(values: Map<String, Long>): List<String> =
        achievements.filter { it.title.isNotBlank() && it.complete(values[it.counter] ?: 0L) }.map { it.title }

    fun validate() {
        if (byCode.size != achievements.size) fail("achievements: duplicate codes")
        achievements.forEach { a ->
            if (a.counter !in Counter.ALL) fail("achievements: ${a.code} counts unknown ${a.counter}")
            if (a.tiers.isEmpty() || a.tiers.size > 3 || a.tiers.zipWithNext().any { (x, y) -> y <= x } || a.tiers.first() <= 0) fail("achievements: tiers of ${a.code}")
        }
        val titles = achievements.map { it.title }.filter { it.isNotBlank() }
        if (titles.toSet().size != titles.size) fail("achievements: a title is given twice")
    }
}

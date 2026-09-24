package features.logic.crafts

import kotlinx.serialization.Serializable
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.random.Random

/** Уровень профессии героя и опыт внутри этого уровня. */
@Serializable
data class ProfessionProgress(val level: Int = 1, val experience: Double = 0.0)

/**
 * Работа, которую ведёт герой (с 0.37.0): одна на героя. [settledAt] - до какого момента
 * (миллисекунды эпохи) циклы уже засчитаны; всё, что после, сервер досчитает при следующем
 * обращении.
 */
@Serializable
data class ActiveWork(val profession: String, val job: String, val startedAt: Long, val settledAt: Long)

/**
 * Что даёт работе снаряжение: инструмент профессии и ветка дерева «Ремесло». Всё в процентах:
 * [speed] ускоряет цикл, [yield] - шанс лишней единицы (каждые 100 - гарантированная), [luck]
 * снижает шанс «ничего», [experience] - опыт профессии, [find] - шанс побочной находки.
 */
@Serializable
data class WorkBonus(
    val speed: Double = 0.0,
    val yield: Double = 0.0,
    val luck: Double = 0.0,
    val experience: Double = 0.0,
    val find: Double = 0.0,
)

/** Что принесли досчитанные циклы. */
@Serializable
data class WorkGains(
    val cycles: Int = 0,
    val nothing: Int = 0,
    val items: Map<String, Long> = emptyMap(),
    val experience: Double = 0.0,
    val levels: Int = 0,
) {
    val isEmpty: Boolean get() = cycles == 0
}

/** Итог досчёта: прогресс профессии после него, до какого момента досчитано, и что добыто. */
data class Settlement(val progress: ProfessionProgress, val settledAt: Long, val gains: WorkGains)

/**
 * Правила ремёсел. Функции чистые: время и [Random] приходят снаружи, поэтому тест проверяет их
 * без базы.
 */
object Crafts {

    /** Сколько опыта от текущего уровня до следующего; на последнем - ничего. */
    fun toNext(rules: CraftsRules, level: Int): Double? =
        if (level >= rules.maxLevel) null else Math.round(rules.experienceBase * level.toDouble().pow(rules.experiencePower)).toDouble()

    private fun levelShare(rules: CraftsRules, level: Int) = (level - 1).toDouble() / (rules.maxLevel - 1)

    /** Длина цикла в миллисекундах: уровень срезает до `levelSpeed` процентов, скорость делит остальное. */
    fun cycleMillis(rules: CraftsRules, job: Job, level: Int, bonus: WorkBonus): Long {
        val seconds = job.seconds * (1 - rules.levelSpeed / 100 * levelShare(rules, level)) / (1 + max(0.0, bonus.speed) / 100)
        return max(500L, Math.round(seconds * 1000))
    }

    /** Шанс цикла не принести ничего, в процентах. */
    fun nothingChance(rules: CraftsRules, job: Job, bonus: WorkBonus): Double =
        job.nothing * (1 - bonus.luck.coerceIn(0.0, rules.luckCap) / 100)

    /** Шанс побочной находки в процентах: уровень и бонус находок поднимают его. */
    fun findChance(rules: CraftsRules, extra: JobExtra, level: Int, bonus: WorkBonus): Double =
        (extra.chance * (1 + rules.levelFind / 100 * levelShare(rules, level)) * (1 + max(0.0, bonus.find) / 100)).coerceAtMost(100.0)

    /**
     * Досчитывает циклы работы с [settledAt] до [now], но не дальше `offlineHours` после
     * [settledAt]: кто не заглядывал дольше, получает только эти часы. Уровень может вырасти на
     * середине - следующие циклы идут уже по нему.
     */
    fun settle(rules: CraftsRules, job: Job, progress: ProfessionProgress, bonus: WorkBonus, settledAt: Long, now: Long, random: Random): Settlement {
        val cap = (rules.offlineHours * 3_600_000).toLong()
        val end = minOf(now, settledAt + cap)
        var level = progress.level
        var experience = progress.experience
        var t = settledAt
        var cycles = 0
        var nothing = 0
        var gained = 0.0
        var levels = 0
        val items = mutableMapOf<String, Long>()
        while (true) {
            val cycle = cycleMillis(rules, job, level, bonus)
            if (t + cycle > end) break
            t += cycle
            cycles++
            if (random.nextDouble() * 100 < nothingChance(rules, job, bonus)) { nothing++; continue }
            val extraUnits = max(0.0, bonus.yield) / 100
            val whole = floor(extraUnits).toLong()
            items.merge(job.output, 1 + whole + if (random.nextDouble() < extraUnits - whole) 1 else 0, Long::plus)
            job.extra.forEach { extra -> if (random.nextDouble() * 100 < findChance(rules, extra, level, bonus)) items.merge(extra.item, 1, Long::plus) }
            val xp = job.experience * (1 + max(0.0, bonus.experience) / 100)
            gained += xp
            experience += xp
            while (true) {
                val next = toNext(rules, level) ?: break
                if (experience < next) break
                experience -= next
                level++
                levels++
            }
            if (toNext(rules, level) == null) experience = 0.0
        }
        // Дольше потолка работа стояла: пропущенное не копится и не досчитывается потом.
        val until = if (now > settledAt + cap) now else t
        return Settlement(ProfessionProgress(level, experience), until, WorkGains(cycles, nothing, items, Math.round(gained * 10) / 10.0, levels))
    }
}

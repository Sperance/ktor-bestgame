package ru.descend.exileforge.features.logic.campaign
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.roll.RolledMonster
import com.sperance.exileforge.rules.sheet.CombatProfile
import kotlinx.serialization.Serializable
import ru.descend.exileforge.extensions.printLog
import ru.descend.exileforge.features.data.hero.Hero
import ru.descend.exileforge.features.data.hero.RunState
import ru.descend.exileforge.features.logic.hero.sheetOf

/** Метка подозрительного захода (1.45.0): когда, где и что не сошлось; решает администратор. */
@Serializable
data class RunFlag(
    val at: Long,
    val zone: String,
    val reason: String,
    val value: Double,
    /** Вес метки (1.63.0): 1-3 по тому, во сколько раз превышен порог; старые метки весят 1. */
    val weight: Int = 1,
)

/**
 * Правдоподобие захода (1.45.0, 1.53.0). Бой считает клиент, сервер верит его журналу: убийства он и так пускает
 * только по семени и по разу, но темп проверяет здесь. С 1.53.0 темп мерится по листу героя: у каждой стаи есть
 * ожидаемое время боя - здоровье её сильнейшего монстра на урон героя в секунду ([Pace]); заход, который идёт быстрее
 * трети ожидаемого (запас [MARGIN] на криты, умения и лаг), помечается [RunFlag]. Дешёвые пороги прежние: убийств в
 * секунду с начала захода и секунд до босса. С 1.63.0 урон героя - сильнейший из профилей [CombatProfile], а метка
 * весит 1-3 по силе нарушения: сумма весов за сутки ниже [AUDIT_SCORE] - только лог, с [AUDIT_SCORE] - запись аудита,
 * с [REJECT_SCORE] невозможное событие отклоняется без награды - номер журнала при этом продвигается, как у любого отказа.
 */
object Plausibility {
    /** Больше убийств в секунду с начала захода не бывает даже у сильнейших героев. */
    const val KILLS_PER_SECOND = 4.0

    /** Босс раньше этого числа секунд с входа - значит, до него не дошли. */
    const val BOSS_SECONDS = 8.0

    /** Ступень Бездны (1.68.0) - волна монстров: быстрее этого числа секунд на ступень её не пройти. */
    const val ABYSS_DEPTH_SECONDS = 4.0

    /** Меток на герое не больше: старые уходят. */
    const val KEEP = 20

    /** Во сколько раз бой может идти быстрее расчётного: криты, умения по площади, питомцы, лаг журнала. */
    const val MARGIN = 3.0

    /** Секунд сверх расчёта, которые заход получает даром: стая у входа и пауза до первого батча. */
    const val SLACK_SECONDS = 10.0

    /**
     * Секунд, которые убийство получает сверх прошедших: клиент шлёт убийство ручного боя, едва враг пал, а не после
     * трёх секунд тишины и конца боя, как прежде, - честный заход мерится так же строго, как до того. Постоянная на
     * заход, а не на убийство: ускоренному клиенту она даёт не больше прежней задержки журнала.
     */
    const val PROMPT_SECONDS = 4.0

    /** С какого веса меток за сутки подозрение пишется в аудит. */
    const val AUDIT_SCORE = 3

    /** С какого веса меток за сутки невозможные события отклоняются. */
    const val REJECT_SCORE = 6
    private const val DAY_MS = 24 * 3_600_000L

    /** Урон героя в секунду по листу: сильнейший из профилей атаки, чар и недугов ([CombatProfile]). */
    fun dps(index: ContentIndex, sheet: Map<String, Double>): Double = CombatProfile.of(index, sheet).best

    /** Ожидаемые секунды боя со стаей: сильнейший её монстр на урон героя - нижняя оценка, удар по площади кладёт стаю разом. */
    fun packSeconds(pack: List<RolledMonster>, dps: Double): Double = (pack.maxOfOrNull { (it.stats["STOCK_HEALTH"] ?: 0.0) + (it.stats["STOCK_ENERGY_SHIELD"] ?: 0.0) } ?: 0.0) / dps

    /** Темп героя на заход: урон в секунду по листу, посчитанный один раз на журнал. */
    class Pace(val dps: Double) {
        constructor(index: ContentIndex, hero: Hero) : this(dps(index, index.sheetOf(hero).stats))
    }

    /**
     * Убийство в стае [pack] по темпу [pace]: работа захода растёт на ожидаемое время первой встречи со стаей и сверяется
     * с прошедшим; `false` - событие отклонить (метка уже стоит, и счёт за сутки не ниже [REJECT_SCORE]).
     */
    fun kill(hero: Hero, state: RunState, now: Long, pace: Pace, pack: List<RolledMonster>, firstOfPack: Boolean): Boolean {
        val seconds = seconds(state, now) + PROMPT_SECONDS
        val kills = state.killed.size + state.vaalKilled.size + 1
        val work = state.work + if (firstOfPack) packSeconds(pack, pace.dps) else 0.0
        val tooFast = when {
            // Первые десяток убийств - стая у входа: темп мерится, когда выборка уже не случайна
            kills > 10 && kills > seconds * KILLS_PER_SECOND ->
                flag(hero, state, now, "kills_per_second", kills / seconds, kills / (seconds * KILLS_PER_SECOND))

            seconds + SLACK_SECONDS < work / MARGIN ->
                flag(hero, state, now, "fight_seconds", work / seconds.coerceAtLeast(0.1), work / MARGIN / (seconds + SLACK_SECONDS))

            else -> false
        }
        if (tooFast) return false
        state.work = work
        return true
    }

    /**
     * Страж захода - босс или (1.68.0) страж порчи: не раньше [BOSS_SECONDS] с входа, и его бой - [guardian] без строк, нижняя
     * оценка - ложится в работу захода, как стая; `false` - событие отклонить.
     */
    fun guardian(hero: Hero, state: RunState, now: Long, pace: Pace, guardian: RolledMonster?): Boolean {
        val elapsed = seconds(state, now)
        val seconds = elapsed + PROMPT_SECONDS
        val work = state.work + (guardian?.let { packSeconds(listOf(it), pace.dps) } ?: 0.0)
        // Одна метка на одно нарушение: слишком ранний страж мерится порогом секунд, остальной - работой захода
        val tooFast = when {
            elapsed < BOSS_SECONDS -> flag(hero, state, now, "boss_seconds", elapsed, BOSS_SECONDS / elapsed)
            seconds + SLACK_SECONDS < work / MARGIN -> flag(hero, state, now, "guardian_seconds", work / seconds, work / MARGIN / (seconds + SLACK_SECONDS))
            else -> false
        }
        if (tooFast) return false
        state.work = work
        return true
    }

    /** Испытание (1.47.0) [place], начатое в [startedAt]: [done] боссов или этажей быстрее [seconds] секунд на каждый - метка [reason]; `false` - отклонить. */
    fun pace(hero: Hero, place: String, startedAt: Long, now: Long, done: Int, seconds: Double, reason: String): Boolean {
        val spent = ((now - startedAt) / 1000.0).coerceAtLeast(0.1)
        return !(done > 0 && spent < done * seconds && flag(hero, place, startedAt, now, reason, spent / done, done * seconds / spent))
    }

    private fun seconds(state: RunState, now: Long): Double = ((now - state.startedAt) / 1000.0).coerceAtLeast(0.1)

    private fun flag(hero: Hero, state: RunState, now: Long, reason: String, value: Double, excess: Double) = flag(hero, state.zone, state.startedAt, now, reason, value, excess)

    /** Вес нарушения по [excess] - во сколько раз превышен порог: до полутора раз 1, до трёх 2, дальше 3. */
    fun weight(excess: Double): Int = when {
        excess < 1.5 -> 1
        excess < 3.0 -> 2
        else -> 3
    }

    /** Сумма весов меток героя за сутки до [now]. */
    fun score(hero: Hero, now: Long): Int = hero.flags.filter { now - it.at < DAY_MS }.sumOf { it.weight }

    /**
     * Ставит метку (одну на заход и причину) весом [weight] от [excess] и говорит, пора ли отклонять: счёт за сутки не ниже
     * [REJECT_SCORE]. Счёт с [AUDIT_SCORE] пишется в журнал аудита.
     */
    private fun flag(hero: Hero, zone: String, startedAt: Long, now: Long, reason: String, value: Double, excess: Double): Boolean {
        if (hero.flags.none { it.zone == zone && it.reason == reason && it.at >= startedAt }) {
            val weight = weight(excess)
            hero.flags += RunFlag(now, zone, reason, Math.round(value * 100) / 100.0, weight)
            while (hero.flags.size > KEEP) hero.flags.removeAt(0)
            printLog("[Plausibility] hero ${hero._id} (${hero.name}) zone $zone: $reason = $value, weight $weight", true)
            val score = score(hero, now)
            if (score >= AUDIT_SCORE) printLog("[Plausibility:AUDIT] hero ${hero._id} (${hero.name}) score $score/$REJECT_SCORE", true)
        }
        return score(hero, now) >= REJECT_SCORE
    }
}

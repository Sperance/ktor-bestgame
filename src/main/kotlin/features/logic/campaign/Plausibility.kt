package features.logic.campaign

import extensions.printLog
import features.data.hero.Hero
import features.data.hero.RunState
import kotlinx.serialization.Serializable

/** Метка подозрительного захода (1.45.0): когда, где и что не сошлось; решает администратор. */
@Serializable
data class RunFlag(val at: Long, val zone: String, val reason: String, val value: Double)

/**
 * Правдоподобие захода (1.45.0). Бой считает клиент, сервер верит его журналу: убийства он и так пускает
 * только по семени и по разу, но темп не проверял. Здесь - дешёвые пороги темпа: убийств в секунду с начала
 * захода и секунд до босса. Нарушение наград не отнимает - герой получает метку [RunFlag], в лог уходит
 * строка, дальше решает администратор (блок-лист).
 */
object Plausibility {
    /** Больше убийств в секунду с начала захода не бывает даже у сильнейших героев. */
    const val KILLS_PER_SECOND = 4.0
    /** Босс раньше этого числа секунд с входа - значит, до него не дошли. */
    const val BOSS_SECONDS = 8.0
    /** Меток на герое не больше: старые уходят. */
    const val KEEP = 20

    /** Проверяет темп убийств захода [state] на момент [now]. */
    fun kills(hero: Hero, state: RunState, now: Long) {
        val seconds = seconds(state, now)
        val kills = state.killed.size + state.vaalKilled.size
        // Первые десяток убийств - стая у входа: темп мерится, когда выборка уже не случайна
        if (kills > 10 && kills > seconds * KILLS_PER_SECOND) flag(hero, state, now, "kills_per_second", kills / seconds)
    }

    /** Проверяет, что до босса захода [state] прошло хоть сколько-то времени. */
    fun boss(hero: Hero, state: RunState, now: Long) {
        val seconds = seconds(state, now)
        if (seconds < BOSS_SECONDS) flag(hero, state, now, "boss_seconds", seconds)
    }

    private fun seconds(state: RunState, now: Long): Double = ((now - state.startedAt) / 1000.0).coerceAtLeast(0.1)

    private fun flag(hero: Hero, state: RunState, now: Long, reason: String, value: Double) {
        if (hero.flags.any { it.zone == state.zone && it.reason == reason && it.at >= state.startedAt }) return
        hero.flags += RunFlag(now, state.zone, reason, Math.round(value * 100) / 100.0)
        while (hero.flags.size > KEEP) hero.flags.removeAt(0)
        printLog("[Plausibility] hero ${hero._id} (${hero.name}) zone ${state.zone}: $reason = $value", true)
    }
}

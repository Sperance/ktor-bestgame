package features.logic.campaign

import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.CoreStat
import com.sperance.exileforge.rules.content.GenericDamage
import com.sperance.exileforge.rules.roll.RolledMonster
import extensions.printLog
import features.data.hero.Hero
import features.data.hero.RunState
import features.logic.hero.sheetOf
import kotlinx.serialization.Serializable

/** Метка подозрительного захода (1.45.0): когда, где и что не сошлось; решает администратор. */
@Serializable
data class RunFlag(val at: Long, val zone: String, val reason: String, val value: Double)

/**
 * Правдоподобие захода (1.45.0, 1.53.0). Бой считает клиент, сервер верит его журналу: убийства он и так пускает
 * только по семени и по разу, но темп проверяет здесь. С 1.53.0 темп мерится по листу героя: у каждой стаи есть
 * ожидаемое время боя - здоровье её сильнейшего монстра на урон героя в секунду ([Pace]); заход, который идёт быстрее
 * трети ожидаемого (запас [MARGIN] на криты, умения и лаг), помечается [RunFlag]. Дешёвые пороги прежние: убийств в
 * секунду с начала захода и секунд до босса. Метка наград не отнимает, пока их меньше [REJECT_AFTER] за сутки; дальше
 * невозможное событие отклоняется без награды - номер журнала при этом продвигается, как у любого отказа правила.
 */
object Plausibility {
    /** Больше убийств в секунду с начала захода не бывает даже у сильнейших героев. */
    const val KILLS_PER_SECOND = 4.0
    /** Босс раньше этого числа секунд с входа - значит, до него не дошли. */
    const val BOSS_SECONDS = 8.0
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
    /** С какой метки за сутки невозможные события отклоняются. */
    const val REJECT_AFTER = 3
    private const val DAY_MS = 24 * 3_600_000L

    /**
     * Урон героя в секунду по листу: сумма ударов всех стихий на скорость атаки с ожиданием крита; без оружия - кулаки.
     * Увеличения урона вообще (1.57.0) уже сложены листом с увеличениями каждого вида удара, как их складывает бой.
     * Крит (1.56.0) - больший из атак и заклинаний: у заклинаний свои шанс и множитель, а журнал не говорит, чем убито.
     */
    fun dps(index: ContentIndex, sheet: Map<String, Double>): Double {
        val combat = index.campaign.combat
        val hit = ATTACKS.sumOf { (sheet[it] ?: 0.0).coerceAtLeast(0.0) }.takeIf { it > 0 } ?: combat.unarmed.damage
        val speed = (sheet["STOCK_ATTACK_SPEED"] ?: 0.0).takeIf { it > 0 }?.coerceIn(0.3, 5.0) ?: combat.unarmed.speed
        val critical = combat.critical
        // Урон крита (1.57.0) растит прибавку крита атак и заклинаний, как в бою.
        val damage = sheet[CoreStat.CRITICAL_DAMAGE.code]
        val attack = critFactor(sheet[CoreStat.CRITICAL_CHANCE.code] ?: critical.chance, critical.effective(sheet[CoreStat.CRITICAL_MULTIPLIER.code] ?: critical.multiplier, damage))
        val spell = critFactor(sheet[CoreStat.SPELL_CRITICAL_CHANCE.code] ?: critical.spellChance, critical.effective(sheet[CoreStat.SPELL_CRITICAL_MULTIPLIER.code] ?: critical.spellMultiplier, damage))
        return (hit * speed * maxOf(attack, spell)).coerceAtLeast(1.0)
    }

    /** Ожидаемый множитель урона от крита: шанс и множитель в процентах. */
    private fun critFactor(chance: Double, multiplier: Double): Double =
        1 + (chance / 100).coerceIn(0.0, 1.0) * (multiplier.coerceAtLeast(100.0) / 100 - 1)

    /** Ожидаемые секунды боя со стаей: сильнейший её монстр на урон героя - нижняя оценка, удар по площади кладёт стаю разом. */
    fun packSeconds(pack: List<RolledMonster>, dps: Double): Double =
        (pack.maxOfOrNull { (it.stats["STOCK_HEALTH"] ?: 0.0) + (it.stats["STOCK_ENERGY_SHIELD"] ?: 0.0) } ?: 0.0) / dps

    /** Темп героя на заход: урон в секунду по листу, посчитанный один раз на журнал. */
    class Pace(val dps: Double) {
        constructor(index: ContentIndex, hero: Hero) : this(dps(index, index.sheetOf(hero).stats))
    }

    /**
     * Убийство в стае [pack] по темпу [pace]: работа захода растёт на ожидаемое время первой встречи со стаей и сверяется
     * с прошедшим; `false` - событие отклонить (метка уже стоит, и за сутки их не меньше [REJECT_AFTER]).
     */
    fun kill(hero: Hero, state: RunState, now: Long, pace: Pace, pack: List<RolledMonster>, firstOfPack: Boolean): Boolean {
        val seconds = seconds(state, now) + PROMPT_SECONDS
        val kills = state.killed.size + state.vaalKilled.size + 1
        val work = state.work + if (firstOfPack) packSeconds(pack, pace.dps) else 0.0
        val tooFast = when {
            // Первые десяток убийств - стая у входа: темп мерится, когда выборка уже не случайна
            kills > 10 && kills > seconds * KILLS_PER_SECOND -> flag(hero, state, now, "kills_per_second", kills / seconds)
            seconds + SLACK_SECONDS < work / MARGIN -> flag(hero, state, now, "fight_seconds", work / seconds.coerceAtLeast(0.1))
            else -> false
        }
        if (tooFast) return false
        state.work = work
        return true
    }

    /** Босс захода [state]: до него прошло хоть сколько-то времени; `false` - событие отклонить. */
    fun boss(hero: Hero, state: RunState, now: Long): Boolean {
        val seconds = seconds(state, now)
        return !(seconds < BOSS_SECONDS && flag(hero, state, now, "boss_seconds", seconds))
    }

    /** Испытание (1.47.0) [place], начатое в [startedAt]: [done] боссов или этажей быстрее [seconds] секунд на каждый - метка [reason]; `false` - отклонить. */
    fun pace(hero: Hero, place: String, startedAt: Long, now: Long, done: Int, seconds: Double, reason: String): Boolean {
        val spent = ((now - startedAt) / 1000.0).coerceAtLeast(0.1)
        return !(done > 0 && spent < done * seconds && flag(hero, place, startedAt, now, reason, spent / done))
    }

    private fun seconds(state: RunState, now: Long): Double = ((now - state.startedAt) / 1000.0).coerceAtLeast(0.1)

    private fun flag(hero: Hero, state: RunState, now: Long, reason: String, value: Double) = flag(hero, state.zone, state.startedAt, now, reason, value)

    /** Ставит метку (одну на заход и причину) и говорит, пора ли отклонять: меток за сутки не меньше [REJECT_AFTER]. */
    private fun flag(hero: Hero, zone: String, startedAt: Long, now: Long, reason: String, value: Double): Boolean {
        if (hero.flags.none { it.zone == zone && it.reason == reason && it.at >= startedAt }) {
            hero.flags += RunFlag(now, zone, reason, Math.round(value * 100) / 100.0)
            while (hero.flags.size > KEEP) hero.flags.removeAt(0)
            printLog("[Plausibility] hero ${hero._id} (${hero.name}) zone $zone: $reason = $value", true)
        }
        return hero.flags.count { now - it.at < DAY_MS } >= REJECT_AFTER
    }

    private val ATTACKS = GenericDamage.HITS
}

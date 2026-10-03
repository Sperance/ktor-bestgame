package ru.descend.exileforge.features.logic.campaign
import com.sperance.exileforge.rules.content.AtlasPoints
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.Counter
import com.sperance.exileforge.rules.content.Region
import com.sperance.exileforge.rules.content.RushPlan
import com.sperance.exileforge.rules.content.Stat
import com.sperance.exileforge.rules.content.TrialEvent
import com.sperance.exileforge.rules.content.TrialEventKind
import com.sperance.exileforge.rules.content.TrialKind
import com.sperance.exileforge.rules.content.TrialProgress
import com.sperance.exileforge.rules.content.TrialRules
import com.sperance.exileforge.rules.content.TrialRun
import com.sperance.exileforge.rules.run.Reward
import com.sperance.exileforge.rules.run.RewardDraws
import com.sperance.exileforge.rules.run.Run
import com.sperance.exileforge.rules.run.RunContext
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import ru.descend.exileforge.base.exception.model.CampaignExceptions
import ru.descend.exileforge.config.ContentStore
import ru.descend.exileforge.features.data.hero.Hero
import ru.descend.exileforge.features.data.hero.HeroRepository
import ru.descend.exileforge.features.logic.hero.Received
import ru.descend.exileforge.features.logic.hero.Rewards

/** Вход в испытание: оно само и контекст героя, замороженный на вход, - по нему клиент ставит монстров. */
@Serializable
data class TrialStart(val run: TrialRun, val context: RunContext)

/** Что принесло одно принятое событие испытания. */
@Serializable
data class TrialReward(val n: Int, val kind: TrialEventKind, val reward: RewardView)

/** Итог журнала испытания: сколько принято, что отклонено, награды по событиям, испытания героя и где он теперь. */
@Serializable
data class TrialReport(
    val applied: Int,
    val rejected: List<Int>,
    val rewards: List<TrialReward>,
    val trials: TrialProgress,
    val level: Int,
    val experience: Double,
    val money: Long,
    val received: Received = Received(),
    /** Испытание, к которому относится отчёт (1.68.0). */
    val runId: String = "",
)

/**
 * Испытания (1.47.0): босс-раш зачищенного региона и бесконечная башня. Бой, как и в зонах, считает клиент; сервер
 * лишь пускает события по порядку - босс раша за боссом, этаж башни за этажом - и катит награды потоком героя:
 * клад на каждом [com.sperance.exileforge.rules.content.TowerRule.hoardEvery]-м этаже и сундук раша в конце.
 * Вход тратит ключ раша (из пяти фрагментов герба) или печать башни; новый вход закрывает прежнее испытание без награды.
 */
class TrialService(
    private val heroes: HeroRepository,
    private val campaign: CampaignService,
    private val content: ContentStore,
) {
    private val index: ContentIndex get() = content.index

    private fun rules(method: String): TrialRules = index.campaign.trials ?: throw CampaignExceptions.funExceptionContent(method, "trials")

    /** Босс-раш региона [regionCode]: все его зоны зачищены, ключ раша тратится. */
    suspend fun rush(heroId: String, regionCode: String): TrialStart {
        val method = "rush"
        val rules = rules(method)
        val hero = heroes.requireHero(heroId, method)
        val region = region(regionCode, method)
        if (!RushPlan.open(region, hero.campaign.cleared)) throw CampaignExceptions.funExceptionRegionClosed(method, regionCode)
        hero.spend(TrialRules.KEY, 1, method)
        return open(hero, TrialKind.RUSH, region.code, 1, method)
    }

    /** Ключ раша (1.48.0): фрагменты герба по правилу раша - в один ключ. */
    suspend fun forgeKey(heroId: String): Hero {
        val method = "forgeKey"
        val rules = rules(method)
        val hero = heroes.requireHero(heroId, method)
        hero.spend(TrialRules.CREST, rules.rush.key.toLong(), method)
        hero.earn(TrialRules.KEY, 1)
        return heroes.save(hero, method)
    }

    /** Башня с последнего чекпоинта: печать тратится. */
    suspend fun tower(heroId: String): TrialStart {
        val method = "tower"
        val rules = rules(method)
        val hero = heroes.requireHero(heroId, method)
        // Башня покорена (1.68.0): этажа после потолка нет - печать не тратится на заход, который не пройти
        val first = rules.tower.start(hero.campaign.trials.towerBest)
        if (first > rules.tower.maxFloor) throw CampaignExceptions.funExceptionTowerConquered(method, rules.tower.maxFloor.toString())
        hero.spend(TrialRules.SEAL, 1, method)
        return open(hero, TrialKind.TOWER, "", first, method)
    }

    private suspend fun open(hero: Hero, kind: TrialKind, region: String, floor: Int, method: String): TrialStart {
        val opened = TrialRun(ObjectId().toHexString(), kind, kotlin.random.Random.nextLong(), hero.level, System.currentTimeMillis(), region, floor)
        val run = opened.copy(context = context(hero, opened))
        hero.campaign.trials = hero.campaign.trials.copy(run = run)
        hero.count(Counter.RUNS)
        hero.count(Counter.TRIALS)
        heroes.save(hero, method)
        return TrialStart(run, run.context!!)
    }

    /** Контекст испытания: замороженный на вход, у испытаний прежних версий - собранный заново. */
    private fun context(hero: Hero, run: TrialRun): RunContext = run.context
        ?: campaign.context(hero, TrialRules.arena(index.campaign, run.heroLevel)).copy(active = null, vaal = null, next = emptyList())

    private fun region(code: String, method: String): Region = index.campaign.regions.firstOrNull { it.code == code } ?: throw CampaignExceptions.funExceptionMapNotFound(method, code)

    /**
     * Журнал испытания: события по номерам, каждый один раз, как у захода. Событие не по правилу (не тот босс, не тот
     * этаж, не тот вид) отклоняется без награды, но номер двигает. Конец испытания закрывает его: раш - сундуком.
     */
    suspend fun events(heroId: String, runId: String, events: List<TrialEvent>): TrialReport {
        val method = "trialEvents"
        if (events.size > MAX_EVENTS) throw CampaignExceptions.funExceptionTooManyEvents(method, MAX_EVENTS.toString())
        val rules = rules(method)
        val hero = heroes.requireHero(heroId, method)
        var run = hero.campaign.trials.run ?: throw CampaignExceptions.funExceptionNoTrial(method, "")
        // Журнал привязан к испытанию (1.68.0): опоздавший пакет прежнего входа не ложится на новый
        if (run.id != runId) throw CampaignExceptions.funExceptionRunMismatch(method, runId)
        val draws = hero.rewards.draws()
        val context = context(hero, run)
        val rejected = mutableListOf<Int>()
        val rewards = mutableListOf<TrialReward>()
        var received = Received()
        for (event in events.sortedBy { it.n }) {
            if (event.n < run.applied) continue
            if (event.n > run.applied) throw CampaignExceptions.funExceptionEventOrder(method, "${event.n}, expected ${run.applied}")
            run = run.copy(applied = run.applied + 1)
            hero.campaign.trials = hero.campaign.trials.copy(run = run)
            val outcome = apply(hero, run, event, rules, context, draws)
            if (outcome == null) {
                rejected += event.n
                continue
            }
            run = hero.campaign.trials.run ?: run
            received += Rewards.grant(hero, outcome, index)
            rewards += TrialReward(event.n, event.kind, RewardView.of(outcome))
            if (hero.campaign.trials.run == null) break
        }
        hero.rewards.drawn = draws.drawn
        heroes.save(hero, method)
        return TrialReport(hero.campaign.trials.run?.applied ?: run.applied, rejected, rewards, hero.campaign.trials, hero.level, hero.experience, hero.money, received, runId)
    }

    /** Одно событие; null - правило его не пустило. */
    private fun apply(hero: Hero, run: TrialRun, event: TrialEvent, rules: TrialRules, context: RunContext, draws: RewardDraws): Reward? {
        val now = System.currentTimeMillis()
        val trials = hero.campaign.trials
        return when (event.kind) {
            TrialEventKind.BOSS -> {
                if (run.kind != TrialKind.RUSH) return null
                val plan = RushPlan(region(run.region, "trialEvents"))
                if (event.index != run.killed || run.killed >= plan.size) return null
                if (!Plausibility.pace(hero, run.region, run.startedAt, now, run.killed + 1, Plausibility.BOSS_SECONDS, "rush_boss_seconds")) return null
                hero.campaign.trials = trials.copy(run = run.copy(killed = run.killed + 1))
                hero.count(Counter.BOSSES)
                hero.count(Counter.RUSH_BOSSES)
                plan.zones.getOrNull(event.index)?.let { hero.stats.add(Stat.BOSS, it.boss) }
                hero.stats.record(Stat.LEVEL_MAX, run.heroLevel.toLong())
                Reward.NONE
            }

            TrialEventKind.FLOOR -> {
                if (run.kind != TrialKind.TOWER || event.index != run.floor) return null
                val tower = rules.tower
                val floor = run.floor
                if (floor > tower.maxFloor) return null
                // Темп - от этажа входа: рекорд растёт по ходу, и счёт от него сбрасывал бы работу на каждом чекпоинте
                if (!Plausibility.pace(hero, TOWER, run.startedAt, now, floor - run.entry + 1, FLOOR_SECONDS, "tower_floor_seconds")) return null
                // Потолок башни (1.53.0): последний этаж пройден - испытание закрыто, как по END
                hero.campaign.trials = trials.copy(
                    run = if (floor >= tower.maxFloor) null else run.copy(floor = floor + 1, hoards = run.hoards + if (tower.hoard(floor)) 1 else 0),
                    towerBest = maxOf(trials.towerBest, floor),
                )
                hero.count(Counter.TOWER_FLOOR, floor.toLong())
                hero.stats.record(Stat.LEVEL_MAX, tower.level(run.heroLevel, floor).toLong())
                if (floor % rules.atlasFloors == 0) AtlasPoints.earn(hero.earned, AtlasPoints.TOWER, floor.toString())
                val abyss = index.campaign.abyss
                if (tower.hoard(floor)) hero.count(Counter.TOWER_HOARDS)
                if (!tower.hoard(floor) || abyss == null) {
                    Reward.NONE
                } else {
                    Run(index, TrialRules.arena(index.campaign, tower.level(run.heroLevel, floor)), run.seed, context)
                        .hoard(tower.hoardDepth(abyss, floor), 1.0, draws, tower.hoardScale(floor), trial = true)
                }
            }

            TrialEventKind.FIGHT -> {
                event.fight?.let { hero.stats.fight(it) { code -> index.monster(code) != null } }
                Reward.NONE
            }

            TrialEventKind.END -> {
                hero.campaign.trials = trials.copy(run = null)
                if (event.fallen) hero.count(Counter.DEATHS)
                if (run.kind == TrialKind.TOWER) Reward.NONE else rushChest(hero, run, rules, context, draws, now)
            }
        }
    }

    /** Сундук раша: по боссам павшим; полная зачистка - в летопись, рекорд времени и первое очко атласа региона. */
    private fun rushChest(hero: Hero, run: TrialRun, rules: TrialRules, context: RunContext, draws: RewardDraws, now: Long): Reward {
        val plan = RushPlan(region(run.region, "trialEvents"))
        val seconds = (now - run.startedAt) / 1000
        val full = run.killed >= plan.size
        val fast = full && seconds <= rules.rush.seconds * plan.size
        if (full) {
            val trials = hero.campaign.trials
            hero.campaign.trials = trials.copy(
                rushBest = trials.rushBest + (run.region to minOf(seconds, trials.rushBest[run.region] ?: Long.MAX_VALUE)),
                rushCleared = (trials.rushCleared + run.region).distinct(),
            )
            hero.count(Counter.RUSH_CLEARS)
            AtlasPoints.earn(hero.earned, AtlasPoints.RUSH, run.region)
        }
        val bosses = plan.zones.mapNotNull { index.monster(it.boss) }
        return Run(index, TrialRules.arena(index.campaign, run.heroLevel), run.seed, context).rushChest(rules.rush, bosses, run.killed, fast, draws)
    }

    private companion object {
        /** Место меток правдоподобия башни. */
        const val TOWER = "tower"

        /** Этаж башни быстрее этого числа секунд - метка правдоподобия. */
        const val FLOOR_SECONDS = 3.0
    }
}

package features.logic.campaign

import base.exception.model.CampaignExceptions
import com.sperance.exileforge.rules.content.AtlasBonuses
import com.sperance.exileforge.rules.content.AtlasPoints
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.CoreStat
import com.sperance.exileforge.rules.content.Counter
import com.sperance.exileforge.rules.content.MonsterRarity
import com.sperance.exileforge.rules.content.Orb
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.content.Slot
import com.sperance.exileforge.rules.content.WorldKind
import com.sperance.exileforge.rules.content.Zone
import com.sperance.exileforge.rules.roll.AbyssRifts
import com.sperance.exileforge.rules.roll.AbyssRun
import com.sperance.exileforge.rules.roll.Chests
import com.sperance.exileforge.rules.roll.Crystal
import com.sperance.exileforge.rules.roll.Dice
import com.sperance.exileforge.rules.roll.EssenceCrystals
import com.sperance.exileforge.rules.roll.ItemInstance
import com.sperance.exileforge.rules.roll.LootRoller
import com.sperance.exileforge.rules.run.RarityBonus
import com.sperance.exileforge.rules.run.Reward
import com.sperance.exileforge.rules.run.RewardDraws
import com.sperance.exileforge.rules.run.Run
import com.sperance.exileforge.rules.run.RunContext
import com.sperance.exileforge.rules.run.RunEvent
import com.sperance.exileforge.rules.run.RunEventKind
import com.sperance.exileforge.rules.run.RunStart
import config.ContentStore
import features.data.hero.Hero
import features.data.hero.HeroRepository
import features.data.hero.RunState
import features.logic.atlas.AtlasService
import features.logic.quests.QuestService
import features.logic.hero.Received
import features.logic.hero.Rewards
import features.logic.hero.sheetOf
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Какие зоны герой прошёл (убил их босса) и какие ему открыты. */
@Serializable
data class CampaignProgress(val cleared: List<String>, val unlocked: List<String>)

/** Что принесли принятые события: опыт, золото, стопки, копии, найденный рецепт. */
@Serializable
data class RewardView(val experience: Double = 0.0, val gold: Long = 0, val items: Map<String, Long> = emptyMap(), val equipment: List<ItemInstance> = emptyList(), val recipe: String? = null) {
    companion object {
        fun of(reward: Reward) = RewardView(reward.experience, reward.gold, reward.items, reward.equipment, reward.recipe)
    }
}

/**
 * Что принесло одно принятое событие журнала [n] вида [kind] (1.30.0): добычу катит только сервер, клиент её показывает.
 * [crystal] (1.30.2) - исход события над кристаллом, меняющего его, а не забирающего (сфера Ваал): клиент подменяет
 * кристалл сразу, не дожидаясь чтения героя; у прочих событий null.
 */
@Serializable
data class EventReward(val n: Int, val kind: RunEventKind, val reward: RewardView, val crystal: CrystalOutcome? = null)

/** Кристалл после события: [index] - его номер в окне зоны, как его назвало событие, [crystal] - каким он стал. */
@Serializable
data class CrystalOutcome(val index: Int, val crystal: Crystal)

/**
 * Итог журнала: сколько событий принято всего ([applied]), какие из присланных отклонены правилом
 * ([rejected], их номера), что принесли остальные - всё вместе ([reward]) и по событиям ([rewards], по
 * одной строке на каждое принятое в этом ответе, по возрастанию номера), потеря опыта смерти и где герой теперь.
 */
@Serializable
data class RunReport(val applied: Int, val rejected: List<Int>, val reward: RewardView, val lost: Double, val level: Int, val experience: Double, val money: Long,
                     val progress: CampaignProgress, val open: Boolean, val received: Received = Received(), val rewards: List<EventReward> = emptyList())

/**
 * Кампания по семени (1.0.0). `start` замораживает контекст героя и выдаёт семя: клиент ставит по нему
 * карту и монстров правилами `rules` и пишет журнал событий; `events` проигрывает журнал тем же кодом,
 * каждый номер один раз, - итог сервера и есть истина. Ни сферы, ни уровня клиент себе не выпишет:
 * он лишь называет, что убил и что открыл.
 *
 * С 1.30.0 добычу семя захода не решает: все награды - убийства, сундуки, босс, кристаллы, порча, Ваал-зона,
 * копилка Бездны - катит сервер потоком наград героя ([features.data.hero.RewardStream]), семени которого
 * клиент не знает, и отвечает наградой каждого события. Номер потока двигает только выданная награда, так
 * что переброс семени захода (гибелью, выходом) не даёт ничего. Новое семя - не чаще правила
 * `run.newSeedSeconds`, чем бы ни закрылся прежний заход; вход без карты в зону открытого захода продолжает
 * его, а заход, начатый на другом контенте, закрывается, а не проигрывается иначе, чем его видел клиент.
 */
class CampaignService : KoinComponent {
    private val heroes: HeroRepository by inject()
    private val atlas: AtlasService by inject()
    private val quests: QuestService by inject()
    private val content: ContentStore by inject()
    private val index: ContentIndex get() = content.index
    private val campaign get() = index.campaign
    private val loot by lazy { LootRoller(index) }

    suspend fun progress(heroId: String): CampaignProgress = progressOf(heroes.requireHero(heroId, "progress"))

    private fun progressOf(hero: Hero) = CampaignProgress(hero.campaign.cleared.filter { it in index.zones }, index.world.unlocked(hero.campaign.cleared))

    private fun openZone(hero: Hero, mapCode: String, method: String): Zone {
        val zone = index.zone(mapCode) ?: throw CampaignExceptions.funExceptionMapNotFound(method, mapCode)
        if (mapCode !in index.world.unlocked(hero.campaign.cleared)) throw CampaignExceptions.funExceptionMapLocked(method, mapCode)
        return zone
    }

    /**
     * Вход в зону. С картой [itemId] она тратится: шаблон должен быть картой этой зоны, строки ложатся
     * на добычу захода и добавляют сундуки, кристаллы и расщелины в окна зоны. Прежний заход и спуск
     * в Бездну закрываются; порча и рецепт открываются заново только с потраченной картой.
     */
    suspend fun start(heroId: String, mapCode: String, itemId: String?): RunStart {
        val method = "start"
        val hero = heroes.requireHero(heroId, method)
        val zone = openZone(hero, mapCode, method)
        val state = hero.campaign
        val now = System.currentTimeMillis()
        // Без карты в ту же зону - тот же заход: вход заново не даёт нового семени
        state.run?.takeIf { itemId == null && it.zone == mapCode && it.content == index.hash }?.let { return startOf(it, zone) }
        // Новое семя - не чаще правила, чем бы ни закрылся прежний заход: выходом, гибелью или брошенный
        val wait = state.seededAt + index.rules.run.newSeedSeconds * 1000L - now
        if (wait > 0) throw CampaignExceptions.funExceptionSeedTooSoon(method, ((wait + 999) / 1000).toString())
        val bonuses = atlas.bonuses(hero)
        val dice = Dice.system()
        val item = itemId?.let { hero.requireItem(it, method) }
        val template = item?.let { index.template(it.template) }
        if (item != null && (template == null || template.slot != Slot.MAP || template.code != loot.mapTemplate(mapCode) || item.equipped))
            throw CampaignExceptions.funExceptionMapItem(method, template?.code ?: item.template)
        val active = item?.let { loot.activeMap(mapCode, loot.mapEffects(it, bonuses.mapEffect), it.rarity) }
        val sheet = index.sheetOf(hero).stats
        val chests = Chests.window(state.chests[mapCode], System.currentTimeMillis(), campaign.chests, sheet[CoreStat.CHEST_QUANTITY.code] ?: 0.0, bonuses.chests, dice)
        state.chests[mapCode] = chests.copy(left = chests.left + (active?.effects?.get(CoreStat.MAP_CHESTS.code)?.toInt() ?: 0))
        val crystals = EssenceCrystals(index).window(state.crystals[mapCode], System.currentTimeMillis(), zone.level, zone.monsters, dice, bonuses)
        val extraCrystals = active?.effects?.get(CoreStat.MAP_CRYSTALS.code)?.toInt() ?: 0
        state.crystals[mapCode] = crystals.copy(crystals = crystals.crystals + List(extraCrystals) { EssenceCrystals(index).roll(zone.level, zone.monsters, dice, bonuses) })
        campaign.abyss?.let { rule ->
            val rifts = AbyssRifts(index)
            val window = rifts.window(state.abyss[mapCode], System.currentTimeMillis(), rule, zone.level, dice, bonuses)
            val extra = active?.effects?.get(CoreStat.MAP_ABYSS_CRACKS.code)?.toInt() ?: 0
            state.abyss[mapCode] = if (extra > 0 && zone.level >= rule.minLevel) window.copy(cracks = window.cracks + List(extra) { rifts.crack(rule, dice, bonuses) }) else window
        }
        state.abyssRun = null
        state.activeMap = active
        if (item != null) {
            hero.items.remove(item)
            state.recipeRolled = false
            state.corruptionOpened = false
            state.vaalZone = null
        }
        val run = RunState(ObjectId().toHexString(), kotlin.random.Random.nextLong(), mapCode, context(hero, zone, sheet), now, content = index.hash)
        state.run = run
        state.seededAt = now
        // Задания на сутки выдаются и здесь: заход, начатый до первого взгляда на доску, тоже идёт в зачёт
        quests.refresh(hero, now, Dice.system())
        hero.count(Counter.RUNS)
        heroes.save(hero, method)
        return startOf(run, zone)
    }

    private fun startOf(run: RunState, zone: Zone) = RunStart(run.id, run.seed, zone.code, zone.level, run.context, Run(index, zone, run.seed, run.context).count,
        run.startedAt, run.applied, run.killed.toList(), run.vaalKilled.toList(), run.tally.copy())

    /** Контекст захода: проценты героя с монстров каждой редкости - лист и силы уникалок, - атлас, карта, Ваал-зона, связи. */
    private fun context(hero: Hero, zone: Zone, sheet: Map<String, Double>): RunContext {
        val powers = index.powers
        val bonuses = MonsterRarity.entries.associate { rarity ->
            fun power(kind: WorldKind) = powers.worldBonus(sheet, kind, rarity)
            rarity.name to RarityBonus(
                quantity = (sheet[CoreStat.QUANTITY.code] ?: 0.0) + power(WorldKind.QUANTITY),
                rarity = (sheet[CoreStat.RARITY.code] ?: 0.0) + power(WorldKind.RARITY),
                experience = (sheet[CoreStat.EXPERIENCE.code] ?: 0.0) + power(WorldKind.EXPERIENCE),
                gold = (sheet[CoreStat.GOLD.code] ?: 0.0) + power(WorldKind.GOLD),
                map = power(WorldKind.MAP), book = power(WorldKind.BOOK), unique = power(WorldKind.UNIQUE),
            )
        }
        val atlasBonuses = atlas.bonuses(hero)
        return RunContext(hero.heroClass, hero.level, bonuses, atlasBonuses.effects, hero.campaign.activeMap?.takeIf { it.mapCode == zone.code },
            hero.campaign.vaalZone?.takeIf { it.mapCode == zone.code }, atlasBonuses.extraRareMods, index.world.next(zone.code), hero.recipes.toList())
    }

    /**
     * Заход с текущими Ваал-зоной и рецептами героя: остальное контекста замёрзло на входе. Строится
     * заново, только когда они сменились, - журнал в сотню убийств не собирает заход сотню раз.
     */
    private inner class Runs(private val hero: Hero, private val state: RunState, private val zone: Zone) {
        private var built: Run? = null

        fun current(): Run {
            val context = state.context.copy(vaal = hero.campaign.vaalZone?.takeIf { it.mapCode == zone.code }, recipes = hero.recipes.toList())
            return built?.takeIf { it.context == context } ?: Run(index, zone, state.seed, context).also { built = it }
        }
    }

    /**
     * Журнал захода: события по номерам, каждый принимается один раз - уже принятые пропускаются, а
     * номер впереди очереди - `CP_019`. Событие, которое правило не пускает (сундуков не осталось, босс
     * мёртв, призыв не по карману), отклоняется без награды, но номер продвигает: журнал клиента не застревает.
     * Награда каждого события ложится на героя сразу (1.30.0): гибель считает потерю от уже выданного опыта.
     */
    suspend fun events(heroId: String, events: List<RunEvent>): RunReport {
        val method = "events"
        val hero = heroes.requireHero(heroId, method)
        // Задания на сутки - до событий: сменившийся день не съедает прирост журнала
        quests.refresh(hero, System.currentTimeMillis(), Dice.system())
        val state = hero.campaign.run ?: throw CampaignExceptions.funExceptionNoRun(method, "")
        if (state.content != index.hash) {
            close(hero.campaign, state.zone)
            heroes.save(hero, method)
            throw CampaignExceptions.funExceptionContentChanged(method, state.id)
        }
        val zone = index.zone(state.zone) ?: throw CampaignExceptions.funExceptionMapNotFound(method, state.zone)
        val runs = Runs(hero, state, zone)
        val bonuses = atlas.bonuses(hero)
        val draws = hero.rewards.draws()
        var total = Reward.NONE
        var lost = 0.0
        var received = Received()
        val rejected = mutableListOf<Int>()
        val rewards = mutableListOf<EventReward>()
        for (event in events.sortedBy { it.n }) {
            if (event.n < state.applied) continue
            if (event.n > state.applied) throw CampaignExceptions.funExceptionEventOrder(method, "${event.n}, expected ${state.applied}")
            val outcome = apply(hero, state, zone, event, runs.current(), bonuses, draws)
            state.applied++
            if (outcome == null) rejected += event.n
            else {
                received += Rewards.grant(hero, outcome.reward, index)
                total += outcome.reward
                lost += outcome.lost
                rewards += EventReward(event.n, event.kind, RewardView.of(outcome.reward), outcome.crystal)
            }
            // Выход или гибель закрыли заход: что журнал прислал после них, уже ни к чему не относится
            if (hero.campaign.run == null) break
        }
        hero.rewards.drawn = draws.drawn
        heroes.save(hero, method)
        return RunReport(state.applied, rejected, RewardView.of(total), lost, hero.level, hero.experience, hero.money, progressOf(hero), hero.campaign.run != null,
            received, rewards)
    }

    private class Outcome(val reward: Reward = Reward.NONE, val lost: Double = 0.0, val crystal: CrystalOutcome? = null)

    /** Одно событие журнала; null - правило его не пустило. Кости наград - из потока героя [draws]. */
    private fun apply(hero: Hero, state: RunState, zone: Zone, event: RunEvent, run: Run, bonuses: AtlasBonuses, draws: RewardDraws): Outcome? {
        val campaignState = hero.campaign
        val mapCode = zone.code
        val now = System.currentTimeMillis()
        return when (event.kind) {
            RunEventKind.KILL -> {
                val key = event.i * Run.PACK_SLOTS + event.m
                val killed = if (event.vaal) state.vaalKilled else state.killed
                if (key in killed) return null
                val reward = run.kill(event.i, event.m, event.vaal, draws) ?: return null
                killed += key
                hero.count(Counter.KILLS)
                when (run.spawn(event.i, event.vaal).pack.getOrNull(event.m)?.rarity) {
                    MonsterRarity.MAGIC -> hero.count(Counter.KILLS_MAGIC)
                    MonsterRarity.RARE -> hero.count(Counter.KILLS_RARE)
                    else -> Unit
                }
                Outcome(reward)
            }
            RunEventKind.CHEST -> {
                val window = campaignState.chests[mapCode] ?: return null
                if (window.left <= 0) return null
                campaignState.chests[mapCode] = window.copy(left = window.left - 1)
                hero.count(Counter.CHESTS)
                state.tally.chests++
                Outcome(run.chest(draws))
            }
            RunEventKind.BOSS -> {
                if (now < (campaignState.bosses[mapCode] ?: 0L)) return null
                campaignState.bosses[mapCode] = now + (bonuses.bossRespawnHours(campaign.bosses.respawnHours) * 3_600_000).toLong()
                if (mapCode !in campaignState.cleared) campaignState.cleared += mapCode
                AtlasPoints.earn(hero.earned, AtlasPoints.BOSS, mapCode)
                hero.count(Counter.BOSSES)
                if (campaignState.activeMap?.takeIf { it.mapCode == mapCode }?.itemRarity == Rarity.RARE) AtlasPoints.earn(hero.earned, AtlasPoints.RARE, mapCode)
                state.tally.bosses++
                Outcome(run.boss(draws))
            }
            RunEventKind.VAAL_OPEN -> {
                if (campaignState.corruptionOpened || campaignState.vaalZone?.mapCode == mapCode) return null
                campaignState.vaalZone = run.vaalZone(draws)
                Outcome()
            }
            RunEventKind.VAAL_LEAVE -> {
                campaignState.vaalZone?.takeIf { it.mapCode == mapCode } ?: return null
                campaignState.corruptionOpened = true
                campaignState.vaalZone = null
                Outcome()
            }
            RunEventKind.CORRUPT -> {
                campaignState.vaalZone?.takeIf { it.mapCode == mapCode } ?: return null
                val reward = run.corrupt(draws) ?: return null
                state.tally.corrupts++
                campaignState.corruptionOpened = true
                campaignState.vaalZone = null
                AtlasPoints.earn(hero.earned, AtlasPoints.VAAL, mapCode)
                hero.count(Counter.VAAL_GUARDIANS)
                Outcome(reward)
            }
            RunEventKind.CRYSTAL -> {
                val window = campaignState.crystals[mapCode] ?: return null
                val crystal = window.crystals.getOrNull(event.index) ?: return null
                campaignState.crystals[mapCode] = window.copy(crystals = window.crystals.filterIndexed { i, _ -> i != event.index })
                hero.count(Counter.CRYSTALS)
                state.tally.crystals++
                Outcome(run.crystal(crystal, draws))
            }
            RunEventKind.CRYSTAL_VAAL -> {
                val window = campaignState.crystals[mapCode] ?: return null
                val crystal = window.crystals.getOrNull(event.index) ?: return null
                if (crystal.vaal || (hero.bag[Orb.VAAL_ORB.name] ?: 0L) < 1) return null
                hero.spend(Orb.VAAL_ORB.name, 1, "crystalVaal")
                val (_, changed) = run.crystalVaal(crystal, draws)
                campaignState.crystals[mapCode] = window.copy(crystals = window.crystals.toMutableList().also { it[event.index] = changed })
                Outcome(crystal = CrystalOutcome(event.index, changed))
            }
            RunEventKind.ABYSS_OPEN -> {
                val rule = campaign.abyss ?: return null
                val window = campaignState.abyss[mapCode] ?: return null
                val crack = window.cracks.getOrNull(event.index) ?: return null
                campaignState.abyss[mapCode] = window.copy(cracks = window.cracks.filterIndexed { i, _ -> i != event.index })
                val depth = campaignState.activeMap?.takeIf { it.mapCode == mapCode }?.effects?.get(CoreStat.MAP_ABYSS_DEPTH.code) ?: 0.0
                campaignState.abyssRun = AbyssRun(mapCode, AbyssRifts(index).depth(rule, crack, depth))
                Outcome()
            }
            RunEventKind.ABYSS_CLAIM -> {
                val descent = campaignState.abyssRun?.takeIf { it.mapCode == mapCode } ?: return null
                if (event.depth !in 0..descent.depth) return null
                campaignState.abyssRun = null
                if (!event.fallen) hero.count(Counter.ABYSS_DEPTH, event.depth.toLong())
                // Гибель в Бездне сжигает копилку целиком; счёт копилок заход ведёт и тогда
                state.tally.hoards++
                Outcome(run.hoard(event.depth, if (event.fallen) 0.0 else 1.0, draws))
            }
            RunEventKind.SUMMON -> {
                if (now >= (campaignState.bosses[mapCode] ?: 0L)) return null
                val price = campaign.services.summonPerLevel * zone.level
                // Не по карману - отказ правила, а не ошибка: журнал за призывом не застревает
                if (hero.money < price) return null
                hero.pay(price)
                campaignState.bosses.remove(mapCode)
                Outcome()
            }
            RunEventKind.FALL -> {
                val lost = loot.deathLoss(campaign.combat.death, zone.level, hero.experience, index.classes.threshold(hero.level) ?: 0.0, index.classes.nextThreshold(hero.level))
                hero.experience -= lost
                hero.count(Counter.DEATHS)
                close(campaignState, mapCode)
                Outcome(lost = lost)
            }
            RunEventKind.LEAVE -> {
                if (now >= (campaignState.bosses[mapCode] ?: 0L)) return null
                close(campaignState, mapCode)
                Outcome()
            }
        }
    }

    /** Заход кончился: карта, с которой герой вошёл, израсходована, спуск, Ваал-зона и заход закрыты. */
    private fun close(state: features.data.hero.CampaignState, mapCode: String) {
        if (state.activeMap?.mapCode == mapCode) state.activeMap = null
        state.abyssRun = null
        state.vaalZone = null
        state.run = null
    }
}

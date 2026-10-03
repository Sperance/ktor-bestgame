package ru.descend.exileforge.features.logic.guild
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.GuildGoal
import com.sperance.exileforge.rules.content.GuildGoalView
import com.sperance.exileforge.rules.content.GuildQuestLog
import com.sperance.exileforge.rules.content.GuildQuests
import com.sperance.exileforge.rules.content.Quest
import com.sperance.exileforge.rules.content.QuestClaimed
import com.sperance.exileforge.rules.content.QuestClock
import com.sperance.exileforge.rules.content.QuestKind
import com.sperance.exileforge.rules.content.QuestReward
import com.sperance.exileforge.rules.roll.Dice
import ru.descend.exileforge.base.exception.model.QuestExceptions
import ru.descend.exileforge.config.ContentStore
import ru.descend.exileforge.features.data.guild.Guild
import ru.descend.exileforge.features.data.guild.GuildRepository
import ru.descend.exileforge.features.data.hero.Hero
import ru.descend.exileforge.features.data.hero.HeroRepository
import ru.descend.exileforge.features.logic.quests.QuestService

/** Гильдейские задания (1.21.0): личные на сутки и общие цели с вкладом каждого участника, их сдача. */
class GuildQuestService(
    private val guilds: GuildRepository,
    private val heroes: HeroRepository,
    private val content: ContentStore,
    private val questService: QuestService,
    private val access: GuildAccess,
) {
    private val index: ContentIndex get() = content.index

    /** Задания гильдии героя: его личные на сутки и общие цели с вкладом каждого участника. */
    suspend fun quests(heroId: String): GuildQuests {
        val method = "guildQuests"
        val change = access.acting(heroId, method)
        val hero = change.actor
        if (questService.refresh(hero, change.now, Dice.system())) change.touch(hero)
        change.save(method)
        return questsView(change, hero)
    }

    /**
     * Награда задания гильдии: личного ([questId]) или доли общей цели ([goal] - её ключ). Опыт гильдии за общую
     * цель начисляется один раз - первым, кто забрал долю; за личное - каждым.
     */
    suspend fun claimQuest(heroId: String, questId: String?, goal: String?): GuildQuests {
        val method = "guildQuestClaim"
        val change = access.acting(heroId, method)
        val hero = change.actor
        questService.refresh(hero, change.now, Dice.system())
        val log = hero.quests.guild ?: throw QuestExceptions.funExceptionNotFound(method, questId ?: goal)
        if (questId != null) {
            val quest = log.quests.firstOrNull { it.id == questId } ?: throw QuestExceptions.funExceptionNotFound(method, questId)
            questService.requireClaimable(quest, method)
            settle(change, hero, quest)
        } else {
            val board = change.guild.quests
            val target = (board.daily + board.weekly).firstOrNull { it.key == goal } ?: throw QuestExceptions.funExceptionNotFound(method, goal)
            if (target.key in log.claimed) throw QuestExceptions.funExceptionClaimed(method, target.key)
            val shares = shares(change, hero, target)
            if (shares.values.sum() < target.target) throw QuestExceptions.funExceptionNotDone(method, target.key)
            val need = need(change.guild, target)
            if ((shares[heroId] ?: 0) < need) throw QuestExceptions.funExceptionShare(method, need.toString())
            settle(change, hero, log, target)
        }
        change.touch(hero)
        change.sync(hero)
        access.commit(change, method, change.grown)
        return questsView(change, hero)
    }

    /**
     * Сдать все гильдейские разом (1.22.0, из «сдать всё» заданий): выполненные личные и долю в каждой закрытой общей
     * цели, где вклад героя не ниже порога. Вне гильдии - ничего.
     */
    suspend fun claimAllQuests(heroId: String, method: String): List<QuestClaimed> {
        if (guilds.byMember(heroId) == null) return emptyList()
        val change = access.acting(heroId, method)
        val hero = change.actor
        questService.refresh(hero, change.now, Dice.system())
        val log = hero.quests.guild ?: return emptyList()
        val claimed = mutableListOf<QuestClaimed>()
        log.quests.filter { it.done && !it.claimed }.forEach { quest ->
            settle(change, hero, quest)
            claimed += questService.claimedOf(quest.id, quest.kind, quest.goal, quest.reward)
        }
        val board = change.guild.quests
        val tallies = tallies(change, hero)
        (board.daily + board.weekly).filter { it.key !in log.claimed }.forEach { target ->
            val shares = shares(tallies, change.guild, target)
            if (shares.values.sum() < target.target || (shares[heroId] ?: 0) < need(change.guild, target)) return@forEach
            claimed += questService.claimedOf(target.key, target.kind, target.goal, settle(change, hero, log, target))
        }
        change.touch(hero)
        change.sync(hero)
        access.commit(change, method, change.grown)
        return claimed
    }

    /** Личное гильдейское: награда на героя, опыт гильдии - за каждое. */
    private fun settle(change: GuildChange, hero: Hero, quest: Quest) {
        questService.grant(hero, quest.reward)
        quest.claimed = true
        change.grow(quest.reward.guildExperience, hero.name)
    }

    /** Доля общей цели: награда по уровню героя; опыт гильдии - один раз, первым, кто забрал долю. */
    private fun settle(change: GuildChange, hero: Hero, log: GuildQuestLog, target: GuildGoal): QuestReward {
        val reward = questService.sharedReward(hero, target)
        questService.grant(hero, reward)
        log.claimed += target.key
        val board = change.guild.quests
        if (target.key !in board.paid) {
            board.paid += target.key
            change.grow(target.guildExperience, hero.name)
        }
        return reward
    }

    private suspend fun questsView(change: GuildChange, hero: Hero): GuildQuests {
        val board = change.guild.quests
        val tallies = tallies(change, hero)
        fun view(goal: GuildGoal): GuildGoalView {
            val shares = shares(tallies, change.guild, goal)
            return GuildGoalView(
                goal,
                shares.values.sum(),
                shares[hero._id] ?: 0,
                need(change.guild, goal),
                goal.key in hero.quests.guild?.claimed.orEmpty(),
                questService.sharedReward(hero, goal),
                shares.filterValues { it > 0 },
            )
        }
        return GuildQuests(
            hero.quests.guild?.quests.orEmpty(),
            board.daily.map(::view),
            board.weekly.map(::view),
            QuestClock.dayEnd(change.now),
            QuestClock.weekEnd(change.now),
            hero.money,
        )
    }

    /** Порог доли: [GuildQuestRule.fairShare] средней доли участника, не меньше единицы. */
    private fun need(guild: Guild, goal: GuildGoal): Long = kotlin.math.ceil(goal.target.toDouble() / guild.members.size.coerceAtLeast(1) * index.quests.guild.fairShare).toLong().coerceAtLeast(1)

    private suspend fun shares(change: GuildChange, hero: Hero, goal: GuildGoal): Map<String, Long> = shares(tallies(change, hero), change.guild, goal)

    /** Вклад участников в цель: их счётчики за сутки или неделю цели, пока они в этой гильдии. */
    private fun shares(tallies: Map<String, GuildQuestLog>, guild: Guild, goal: GuildGoal): Map<String, Long> {
        val daily = goal.kind == QuestKind.GUILD_DAILY
        return guild.members.associate { member ->
            val tally = tallies[member.heroId]?.takeIf { it.id == guild._id }
            val value = when {
                tally == null -> 0L
                daily -> if (tally.day == guild.quests.day) tally.dayCounts[goal.counter] ?: 0L else 0L
                else -> if (tally.week == guild.quests.week) tally.weekCounts[goal.counter] ?: 0L else 0L
            }
            member.heroId to value
        }
    }

    /** Гильдейские счётчики участников; свой - из героя в памяти, он свежее базы. */
    private suspend fun tallies(change: GuildChange, hero: Hero): Map<String, GuildQuestLog> = heroes.guildTallies(change.guild.members.map { it.heroId }.filter { it != hero._id }) + listOfNotNull(hero.quests.guild?.let { hero._id to it })
}

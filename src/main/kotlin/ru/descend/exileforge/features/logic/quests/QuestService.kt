package ru.descend.exileforge.features.logic.quests
import com.sperance.exileforge.rules.content.AtlasPoints
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.Counter
import com.sperance.exileforge.rules.content.GuildGoal
import com.sperance.exileforge.rules.content.Quest
import com.sperance.exileforge.rules.content.QuestBoard
import com.sperance.exileforge.rules.content.QuestClaimAll
import com.sperance.exileforge.rules.content.QuestClaimed
import com.sperance.exileforge.rules.content.QuestClock
import com.sperance.exileforge.rules.content.QuestCondition
import com.sperance.exileforge.rules.content.QuestCounter
import com.sperance.exileforge.rules.content.QuestGoal
import com.sperance.exileforge.rules.content.QuestKind
import com.sperance.exileforge.rules.content.QuestLog
import com.sperance.exileforge.rules.content.QuestProgress
import com.sperance.exileforge.rules.content.QuestReward
import com.sperance.exileforge.rules.content.QuestRules
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.roll.Dice
import com.sperance.exileforge.rules.text.LocaleKey
import org.bson.types.ObjectId
import ru.descend.exileforge.base.exception.model.QuestExceptions
import ru.descend.exileforge.config.ContentStore
import ru.descend.exileforge.features.data.hero.Hero
import ru.descend.exileforge.features.data.hero.HeroRepository
import ru.descend.exileforge.features.logic.guild.GuildQuestService
import ru.descend.exileforge.features.logic.hero.Rewards

/**
 * Задания героя (1.21.0): выдача по суткам и неделям UTC, доска контрактов, сюжет по регионам, личные гильдейские.
 * Всё выдаётся лениво - при чтении доски, входе в заход и чтении заданий гильдии ([refresh]); прогресс двигает
 * `Hero.count`, выводимые цели досчитываются здесь же. Награда выроллена при выдаче и видна заранее.
 */
class QuestService(
    private val heroes: HeroRepository,
    private val content: ContentStore,
    private val engine: QuestEngine,
    private val guilds: GuildQuestService,
) {
    private val index: ContentIndex get() = content.index
    private val rules: QuestRules get() = index.quests

    suspend fun board(heroId: String): QuestBoard {
        val hero = heroes.requireHero(heroId, "quests")
        val now = System.currentTimeMillis()
        if (engine.refresh(hero, now, Dice.system())) heroes.save(hero, "quests")
        return engine.view(hero, now)
    }

    /** Награда за выполненное: контракт уходит с доски, шаг сюжета сменяется следующим. Гильдейские забираются через гильдию. */
    suspend fun claim(heroId: String, questId: String): QuestBoard = command(heroId, "questClaim") { hero, log, now, dice ->
        val quest = log.find(questId)?.takeIf { it.kind != QuestKind.GUILD } ?: throw QuestExceptions.funExceptionNotFound("questClaim", questId)
        engine.requireClaimable(quest, "questClaim")
        settle(hero, log, quest, now, dice)
    }

    /**
     * Сдать всё выполненное (1.22.0): долю героя в гильдейских - через гильдию, своей транзакцией, затем личные -
     * ежедневные, недельные, контракты и шаги сюжета подряд, пока следующий шаг уже выполнен.
     */
    suspend fun claimAll(heroId: String): QuestClaimAll {
        val method = "questClaimAll"
        val claimed = guilds.claimAllQuests(heroId, method).toMutableList()
        val board = command(heroId, method) { hero, log, now, dice ->
            while (true) {
                val quest = log.personal().firstOrNull { it.done && !it.claimed } ?: break
                settle(hero, log, quest, now, dice)
                claimed += engine.claimedOf(quest.id, quest.kind, quest.goal, quest.reward)
            }
        }
        return QuestClaimAll(claimed, board, board.money)
    }

    /** Награда личного задания на героя: контракт уходит с доски, шаг сюжета сменяется следующим. */
    private fun settle(hero: Hero, log: QuestLog, quest: Quest, now: Long, dice: Dice) {
        engine.grant(hero, quest.reward)
        quest.claimed = true
        when (quest.kind) {
            QuestKind.CONTRACT -> log.contracts.remove(quest)

            QuestKind.STORY -> {
                log.story = null
                log.step++
                if (log.step >= rules.story.getOrNull(log.chapter)?.steps?.size ?: 0) {
                    log.chapter++
                    log.step = 0
                }
                engine.refresh(hero, now, dice)
            }

            else -> Unit
        }
    }

    /** Взять листок с доски: срок контракта и начало выводимой цели (1.30.0) считаются с этой минуты. */
    suspend fun take(heroId: String, offerId: String): QuestBoard = command(heroId, "questTake") { hero, log, now, _ ->
        val method = "questTake"
        val offer = log.offers.firstOrNull { it.id == offerId } ?: throw QuestExceptions.funExceptionOffer(method, offerId)
        if (log.contracts.size >= rules.board.active) throw QuestExceptions.funExceptionContracts(method, log.contracts.size.toString())
        log.offers.remove(offer)
        val start = if (offer.derived) engine.derivedValue(hero, offer.counter, offer.place) else offer.start
        log.contracts += offer.copy(expiresAt = now + engine.hours(offer.rarity), start = start).also { engine.derive(hero, it) }
    }

    /** Отказ от контракта: листок сгорает. */
    suspend fun abandon(heroId: String, questId: String): QuestBoard = command(heroId, "questAbandon") { _, log, _, _ ->
        if (!log.contracts.removeIf { it.id == questId }) throw QuestExceptions.funExceptionNotFound("questAbandon", questId)
    }

    private suspend fun command(heroId: String, method: String, block: (Hero, QuestLog, Long, Dice) -> Unit): QuestBoard {
        val hero = heroes.requireHero(heroId, method)
        val now = System.currentTimeMillis()
        val dice = Dice.system()
        engine.refresh(hero, now, dice)
        block(hero, hero.quests, now, dice)
        heroes.save(hero, method)
        return engine.view(hero, now)
    }
}

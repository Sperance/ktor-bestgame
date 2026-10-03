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
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import ru.descend.exileforge.base.exception.model.QuestExceptions
import ru.descend.exileforge.config.ContentStore
import ru.descend.exileforge.features.data.guild.GuildRepository
import ru.descend.exileforge.features.data.hero.Hero
import ru.descend.exileforge.features.data.hero.HeroRepository
import ru.descend.exileforge.features.logic.hero.Rewards

/**
 * Задания героя (1.21.0): выдача по суткам и неделям UTC, доска контрактов, сюжет по регионам, личные гильдейские.
 * Всё выдаётся лениво - при чтении доски, входе в заход и чтении заданий гильдии ([refresh]); прогресс двигает
 * `Hero.count`, выводимые цели досчитываются здесь же. Награда выроллена при выдаче и видна заранее.
 */
class QuestService : KoinComponent {
    private val heroes: HeroRepository by inject()
    private val guilds: GuildRepository by inject()
    private val content: ContentStore by inject()
    private val index: ContentIndex get() = content.index
    private val rules: QuestRules get() = index.quests

    suspend fun board(heroId: String): QuestBoard {
        val hero = heroes.requireHero(heroId, "quests")
        val now = System.currentTimeMillis()
        if (refresh(hero, now, Dice.system())) heroes.save(hero, "quests")
        return view(hero, now)
    }

    /** Награда за выполненное: контракт уходит с доски, шаг сюжета сменяется следующим. Гильдейские забираются через гильдию. */
    suspend fun claim(heroId: String, questId: String): QuestBoard = command(heroId, "questClaim") { hero, log, now, dice ->
        val quest = log.find(questId)?.takeIf { it.kind != QuestKind.GUILD } ?: throw QuestExceptions.funExceptionNotFound("questClaim", questId)
        requireClaimable(quest, "questClaim")
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
                claimed += claimedOf(quest.id, quest.kind, quest.goal, quest.reward)
            }
        }
        return QuestClaimAll(claimed, board, board.money)
    }

    /** Что сдано - для ответа «сдать всё»: название ключом словаря. */
    fun claimedOf(id: String, kind: QuestKind, goal: String, reward: QuestReward) = QuestClaimed(id, kind, LocaleKey.questTitle(kind, goal), reward)

    /** Награда личного задания на героя: контракт уходит с доски, шаг сюжета сменяется следующим. */
    private fun settle(hero: Hero, log: QuestLog, quest: Quest, now: Long, dice: Dice) {
        grant(hero, quest.reward)
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
                refresh(hero, now, dice)
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
        val start = if (offer.derived) derivedValue(hero, offer.counter, offer.place) else offer.start
        log.contracts += offer.copy(expiresAt = now + hours(offer.rarity), start = start).also { derive(hero, it) }
    }

    /** Отказ от контракта: листок сгорает. */
    suspend fun abandon(heroId: String, questId: String): QuestBoard = command(heroId, "questAbandon") { _, log, _, _ ->
        if (!log.contracts.removeIf { it.id == questId }) throw QuestExceptions.funExceptionNotFound("questAbandon", questId)
    }

    private suspend fun command(heroId: String, method: String, block: (Hero, QuestLog, Long, Dice) -> Unit): QuestBoard {
        val hero = heroes.requireHero(heroId, method)
        val now = System.currentTimeMillis()
        val dice = Dice.system()
        refresh(hero, now, dice)
        block(hero, hero.quests, now, dice)
        heroes.save(hero, method)
        return view(hero, now)
    }

    // ==================== ВЫДАЧА ====================

    /**
     * Задания на сейчас: сменились сутки или неделя - новые, доска пополнена, просроченное снято, сюжет на месте,
     * выводимые цели досчитаны, гильдейские - по гильдии героя. true - герой изменился.
     */
    fun refresh(hero: Hero, now: Long, dice: Dice): Boolean {
        val log = hero.quests
        val before = log.copy(
            daily = log.daily.map { it.copy() }.toMutableList(),
            weekly = log.weekly.map { it.copy() }.toMutableList(),
            offers = log.offers.toMutableList(),
            contracts = log.contracts.map { it.copy() }.toMutableList(),
            story = log.story?.copy(),
            guild = log.guild?.let { it.copy(quests = it.quests.map { q -> q.copy() }.toMutableList(), claimed = it.claimed.toMutableList()) },
        )
        val day = QuestClock.day(now)
        val week = QuestClock.week(now)
        if (log.day != day) {
            log.day = day
            log.daily = rollMany(hero, QuestKind.DAILY, dice, now)
        }
        if (log.week != week) {
            log.week = week
            log.weekly = rollMany(hero, QuestKind.WEEKLY, dice, now)
        }
        log.contracts.removeIf { it.expiresAt in 1..now && !it.done }
        log.offers.removeIf { it.expiresAt in 1..now }
        refill(hero, log, now, dice)
        if (log.story == null) log.story = storyStep(hero, log)
        refreshGuild(hero, log, day, week, now, dice)
        log.active().forEach { derive(hero, it) }
        return log != before
    }

    /** Личные гильдейские на сутки; вне гильдии гильдейской части нет, смена гильдии обнуляет только вклад. */
    private fun refreshGuild(hero: Hero, log: QuestLog, day: Long, week: Long, now: Long, dice: Dice) {
        val guildId = hero.guild?.id
        if (guildId == null) {
            log.guild = null
            return
        }
        val guild = QuestProgress.guildLog(log, guildId, day, week)
        if (guild.rolled != day) {
            guild.rolled = day
            guild.quests = rollMany(hero, QuestKind.GUILD, dice, now)
        }
        guild.claimed.removeIf { !it.startsWith(dayKey(day)) && !it.startsWith(weekKey(week)) }
    }

    private fun refill(hero: Hero, log: QuestLog, now: Long, dice: Dice) {
        val step = rules.board.refillHours * QuestClock.HOUR
        val due = if (log.refilledAt <= 0) rules.board.size.toLong() else (now - log.refilledAt) / step
        if (due <= 0) return
        repeat(minOf(due, (rules.board.size - log.offers.size).toLong()).toInt()) { roll(hero, QuestKind.CONTRACT, dice, now)?.let { log.offers += it } }
        log.refilledAt = if (log.refilledAt <= 0 || log.offers.size >= rules.board.size) now else log.refilledAt + due * step
    }

    private fun storyStep(hero: Hero, log: QuestLog): Quest? {
        val chapter = rules.story.getOrNull(log.chapter) ?: return null
        val step = chapter.steps.getOrNull(log.step) ?: return null
        val region = index.campaign.regions.first { it.code == chapter.region }
        val level = step.zone.takeIf { it.isNotEmpty() }?.let { index.zone(it)?.level } ?: region.zones.maxOf { it.level }
        // Задания не привязаны к зоне (1.52.0): зона шага - лишь место в подписи, счёт идёт в любой.
        return Quest(
            ObjectId().toHexString(),
            QuestKind.STORY,
            step.code,
            step.counter,
            step.rarity,
            step.target,
            place = step.zone.ifEmpty { region.code },
            reward = reward(hero, QuestKind.STORY, step.rarity, minOf(level, hero.level).coerceAtLeast(1), Dice.system()),
        ).also { derive(hero, it) }
    }

    private fun rollMany(hero: Hero, kind: QuestKind, dice: Dice, now: Long): MutableList<Quest> {
        val quests = mutableListOf<Quest>()
        repeat(rules.kind(kind)?.count ?: 0) { roll(hero, kind, dice, now, quests.map { it.goal }.toSet())?.let { quests += it } }
        return quests
    }

    /** Одно задание вида [kind]: редкость по весам, цель без повтора [taken], зоны и условия, награда. */
    fun roll(hero: Hero, kind: QuestKind, dice: Dice, now: Long, taken: Set<String> = emptySet()): Quest? {
        val kindRule = rules.kind(kind) ?: return null
        val rarity = weighted(kindRule.rarities.mapNotNull { rules.rarity(it) }, dice) { it.weight }?.rarity ?: return null
        val rarityRule = rules.rarity(rarity)!!
        val level = hero.level
        fun targetOf(goal: QuestGoal): Long {
            val target = ((goal.base + goal.perLevel * level) * rarityRule.target * kindRule.target).toLong().coerceAtLeast(1)
            return if (goal.max > 0) target.coerceAtMost(goal.max) else target
        }
        val candidates = rules.goals.filter { kind in it.kinds && it.minLevel <= hero.level && it.code !in taken && feasible(hero, it.counter, targetOf(it)) }
        val goal = weighted(candidates, dice) { it.weight } ?: return null
        val target = targetOf(goal)
        val conditions = if (!goal.conditional) emptyList() else dice.shuffled(QuestCondition.entries).take(rarityRule.conditions)
        val start = if (goal.counter in QuestCounter.DERIVED) derivedValue(hero, goal.counter, "") else 0
        return Quest(
            ObjectId().toHexString(), kind, goal.code, goal.counter, rarity, target, start = start,
            conditions = conditions.sortedBy { it.ordinal }, reward = reward(hero, kind, rarity, level, dice),
            expiresAt = if (kind == QuestKind.CONTRACT) now + hours(rarity) else 0,
        )
    }

    /** Награда: золото и опыт по уровню и множителям, сферы - из таблицы редкости; гильдейскому - знаки и опыт гильдии. */
    fun reward(hero: Hero, kind: QuestKind, rarity: Rarity, level: Int, dice: Dice): QuestReward {
        val kindRule = rules.kind(kind)!!
        val rarityRule = rules.rarity(rarity)!!
        val scale = rarityRule.reward * kindRule.reward
        val gold = rules.gold(level, scale)
        val orbs = HashMap<String, Long>()
        repeat(kindRule.orbs) { weighted(rarityRule.orbs, dice) { it.weight }?.let { orbs.merge(it.code, it.amount, Long::plus) } }
        // Сундук-добыча (1.71.0): после сфер, чтобы их броски не сдвинулись.
        if (rarityRule.chests.isNotEmpty() && dice.chance(rarityRule.chestChance)) {
            weighted(rarityRule.chests, dice) { it.weight }?.let { chest ->
                com.sperance.exileforge.rules.roll.LootChests(index).code(chest.code, level)?.let { orbs.merge(it, chest.amount, Long::plus) }
            }
        }
        return QuestReward(
            gold,
            rules.experience(index.classes, level, scale),
            orbs,
            guildExperience = if (kind == QuestKind.GUILD) (gold * rules.guild.experience).toLong() else 0,
        )
    }

    /** Награда ложится на героя; золото не двигает задания «заработать» - иначе награда платила бы сама за себя. */
    fun grant(hero: Hero, reward: QuestReward) {
        hero.money += reward.gold
        Counter.add(hero.counters, Counter.GOLD_EARNED, reward.gold)
        if (reward.experience > 0) Rewards.addExperience(hero, reward.experience, index)
        reward.orbs.forEach { (code, amount) -> if (index.item(code) != null) hero.earn(code, amount) }
    }

    /**
     * Общие цели гильдии вида [kind] с ключами `<prefix><номер>`: цель = база на уровне `guild.sharedLevel` × [members] ×
     * множители; опыт гильдии - золото на том же уровне × участники × `guild.experience`.
     */
    fun sharedGoals(kind: QuestKind, prefix: String, members: Int, dice: Dice): MutableList<GuildGoal> {
        val kindRule = rules.kind(kind) ?: return mutableListOf()
        val level = rules.guild.sharedLevel.coerceAtLeast(1)
        val count = members.coerceAtLeast(1)
        val goals = mutableListOf<GuildGoal>()
        repeat(kindRule.count) { number ->
            val rarityRule = weighted(kindRule.rarities.mapNotNull { rules.rarity(it) }, dice) { it.weight } ?: return@repeat
            val goal = weighted(rules.goals.filter { g -> kind in g.kinds && g.minLevel <= level && goals.none { it.goal == g.code } }, dice) { it.weight } ?: return@repeat
            val target = ((goal.base + goal.perLevel * level) * count * rarityRule.target * kindRule.target).toLong().coerceAtLeast(1)
            val orbs = HashMap<String, Long>()
            repeat(kindRule.orbs) { weighted(rarityRule.orbs, dice) { it.weight }?.let { orbs.merge(it.code, it.amount, Long::plus) } }
            val experience = (rules.gold(level, rarityRule.reward * kindRule.reward) * count * rules.guild.experience).toLong()
            goals += GuildGoal("$prefix$number", kind, goal.code, goal.counter, rarityRule.rarity, target, orbs, experience)
        }
        return goals
    }

    /** Доля участника в общей цели: золото и опыт по его уровню, сферы - закреплённые целью. */
    fun sharedReward(hero: Hero, goal: GuildGoal): QuestReward {
        val scale = (rules.rarity(goal.rarity)?.reward ?: 1.0) * (rules.kind(goal.kind)?.reward ?: 1.0)
        return QuestReward(rules.gold(hero.level, scale), rules.experience(index.classes, hero.level, scale), goal.orbs)
    }

    fun requireClaimable(quest: Quest, method: String) {
        if (quest.claimed) throw QuestExceptions.funExceptionClaimed(method, quest.id)
        if (!quest.done) throw QuestExceptions.funExceptionNotDone(method, quest.id)
    }

    fun view(hero: Hero, now: Long): QuestBoard {
        val log = hero.quests
        val full = log.offers.size >= rules.board.size
        return QuestBoard(
            log.daily, log.weekly, log.offers, log.contracts, log.story, log.chapter, log.step,
            QuestClock.dayEnd(now), QuestClock.weekEnd(now),
            if (full || log.refilledAt <= 0) 0 else log.refilledAt + rules.board.refillHours * QuestClock.HOUR,
            rules.board.active, hero.money,
        )
    }

    // ==================== ПОМОЩНИКИ ====================

    private fun hours(rarity: Rarity): Long = (rules.rarity(rarity)?.contractHours ?: 1) * QuestClock.HOUR

    /**
     * Выводимые цели считаются от состояния героя: прогресс - прирост с выдачи (шаг сюжета - само значение). Узлы
     * дерева и атласа, уровень и зоны меряются рекордом героя (1.30.0): откат и повторное взятие узлов цель не двигают.
     */
    private fun derive(hero: Hero, quest: Quest) {
        if (!quest.derived || quest.claimed) return
        val value = derivedValue(hero, quest.counter, quest.place)
        quest.progress = (value - quest.start).coerceIn(0, quest.target)
    }

    private fun derivedValue(hero: Hero, counter: String, zone: String): Long = QuestProgress.derived(counter, hero.chronicle(), hero.campaign.cleared, zone)

    /** Рекорд выводимого счётчика по летописи героя. */
    private fun peak(hero: Hero, counter: String): Long = hero.chronicle()[counter] ?: 0L

    /**
     * Недостижимое не выдаётся (1.30.0 - по посчитанной цели [target]): уровень и зоны до потолка, узлы атласа - в
     * пределах очков, что вообще можно заработать, и узлов атласа; узлы дерева - в пределах очков до потолка уровня.
     */
    private fun feasible(hero: Hero, counter: String, target: Long): Boolean = when (counter) {
        QuestCounter.LEVEL -> hero.level + target <= index.classes.maxLevel

        QuestCounter.ZONES -> hero.campaign.cleared.size + target <= index.campaign.zones.size

        QuestCounter.ATLAS -> {
            val atlas = index.atlas
            val earnable = index.zones.size.toLong() * AtlasPoints.KINDS.sumOf { atlas.points[it] ?: 0 }
            val reachable = minOf(earnable, atlas.cap.toLong(), atlas.nodes.size - 1L)
            peak(hero, counter) + target <= reachable
        }

        QuestCounter.TREE -> {
            val points = index.classes.pointsTotal(index.classes.maxLevel) - index.tree.spent(hero.tree)
            peak(hero, counter) + target <= minOf(hero.tree.size.toLong() + points, index.tree.byCode.size.toLong())
        }

        else -> true
    }

    private fun <T> weighted(items: List<T>, dice: Dice, weight: (T) -> Int): T? {
        val total = items.sumOf(weight)
        if (total <= 0) return null
        var left = dice.nextInt(total)
        return items.firstOrNull {
            left -= weight(it)
            left < 0
        }
    }

    companion object {
        fun dayKey(day: Long) = "D$day:"
        fun weekKey(week: Long) = "W$week:"
    }
}

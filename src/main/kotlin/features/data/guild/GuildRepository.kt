package features.data.guild

import base.exception.BaseException
import base.exception.model.CharacterExceptions
import base.exception.model.GuildExceptions
import base.exception.model.QuestExceptions
import base.repository.BaseRepository
import base.repository.IndexSpec
import base.route.PagedMongoResponse
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.GuildLogKind
import com.sperance.exileforge.rules.content.GuildMode
import com.sperance.exileforge.rules.content.GuildRole
import com.sperance.exileforge.rules.content.GuildRules
import com.sperance.exileforge.rules.content.GuildGoal
import com.sperance.exileforge.rules.content.GuildGoalView
import com.sperance.exileforge.rules.content.GuildQuestLog
import com.sperance.exileforge.rules.content.GuildQuests
import com.sperance.exileforge.rules.content.Item
import com.sperance.exileforge.rules.content.Quest
import com.sperance.exileforge.rules.content.QuestClaimed
import com.sperance.exileforge.rules.content.QuestReward
import com.sperance.exileforge.rules.content.QuestClock
import com.sperance.exileforge.rules.content.QuestKind
import com.sperance.exileforge.rules.roll.Dice
import config.ContentStore
import config.MongoFactory.transactionExecute
import extensions.printLog
import kotlinx.coroutines.flow.firstOrNull
import features.data.hero.Hero
import features.data.hero.HeroCard
import features.data.hero.HeroGuild
import features.data.hero.HeroRepository
import features.logic.quests.QuestService
import org.bson.Document
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.regex.Pattern

/**
 * Гильдии (1.20.0). Правда о составе - документ гильдии; у героя лежит копия [HeroGuild],
 * и каждое чтение гильдии героем сверяет её. Любое чтение и запись гильдии сначала обслуживают её: отмечают,
 * что участник заходил, и передают главенство, если глава не заходил дольше `leaderIdleDays`.
 */
class GuildRepository : BaseRepository<Guild>(Guild::class), KoinComponent {
    private val heroes: HeroRepository by inject()
    private val events: GuildEventRepository by inject()
    private val chats: GuildChatRepository by inject()
    private val content: ContentStore by inject()
    private val questService: QuestService by inject()
    private val index: ContentIndex get() = content.index
    private val rules: GuildRules get() = index.guilds

    override val indexes = listOf(
        IndexSpec.unique("idx_unique_guild_name", "nameKey"),
        IndexSpec.unique("idx_unique_guild_tag", "tag"),
        IndexSpec.on("members.heroId"),
        IndexSpec.on("applications.heroId"),
        IndexSpec.on("invites.heroId"),
    )

    // ==================== ЧТЕНИЕ ====================

    /** Гильдия героя глазами героя; вне гильдии - приглашения и когда можно вступить снова. */
    suspend fun mine(heroId: String): GuildMine {
        val hero = heroes.requireHero(heroId, "guildMine")
        val guild = guildOf(heroId)
        if (guild == null) {
            heal(hero)
            return outside(hero)
        }
        val change = Change(guild)
        change.visit(hero)
        change.saveQuietly("mine")
        return inside(change, hero)
    }

    /** Поиск по имени или тегу [text]; [faction] - только гильдии этой фракции. Порядок - по опыту (рейтинг). */
    suspend fun search(heroId: String, text: String?, faction: String?, page: Int, size: Int): PagedMongoResponse<GuildCard> {
        heroes.requireHero(heroId, "guildSearch")
        val filters = listOfNotNull(
            text?.trim()?.takeIf { it.isNotEmpty() }?.let { Filters.or(Filters.regex("name", Pattern.quote(it), "i"), Filters.eq("tag", it.uppercase())) },
            faction?.trim()?.takeIf { it.isNotEmpty() }?.let { Filters.eq("faction", it) },
        )
        val filter = if (filters.isEmpty()) Filters.empty() else Filters.and(filters)
        val found = findPaged(filter, page, size, Sorts.orderBy(Sorts.descending("experience"), Sorts.ascending("_id")))
        return PagedMongoResponse(found.items.map(::card), found.page, found.totalItems, found.totalPages)
    }

    suspend fun log(heroId: String, page: Int, size: Int): List<GuildLogEntry> {
        val change = reading(heroId, "guildLog")
        return events.page(change.guild._id, page, size)
    }

    suspend fun chat(heroId: String, after: Long): List<GuildMessage> {
        val change = reading(heroId, "guildChat")
        return chats.after(change.guild._id, after, rules.chat.keep).map(::message)
    }

    // ==================== ОСНОВАНИЕ И СОСТАВ ====================

    suspend fun create(heroId: String, name: String, tag: String, faction: String, emblem: String, color: String, mode: GuildMode, minLevel: Int): GuildMine {
        val method = "guildCreate"
        val hero = requireFree(heroId, method)
        if (hero.level < rules.create.level) throw GuildExceptions.funExceptionCreateLevel(method, rules.create.level.toString())
        val title = requireName(name, method)
        val code = requireTag(tag, method)
        if (rules.faction(faction) == null) throw GuildExceptions.funExceptionFaction(method, faction)
        requireLook(emblem, color, method)
        requireMinLevel(minLevel, method)
        if (hero.money < rules.create.gold) throw CharacterExceptions.funExceptionGold(method, rules.create.gold.toString())
        hero.pay(rules.create.gold)
        val guild = Guild(name = title, tag = code, faction = faction, emblem = emblem, color = color, mode = mode, minLevel = minLevel)
        val change = Change(guild)
        change.enlist(hero, GuildRole.LEADER)
        change.log(GuildLogKind.CREATED, hero.name, title)
        transactionExecute("guild $method") { session ->
            insert(guild, session)
            change.write(session, fresh = true)
            forgetPending(hero._id, guild._id, session)
        }
        return inside(change, hero)
    }

    /** Вступление в открытую гильдию. */
    suspend fun join(heroId: String, guildId: String): GuildMine {
        val method = "guildJoin"
        val hero = requireFree(heroId, method)
        val change = Change(requireGuild(guildId, method)).also { it.maintain() }
        if (change.guild.mode != GuildMode.OPEN) throw GuildExceptions.funExceptionMode(method, change.guild.mode.name)
        requireLevel(change.guild, hero, method)
        return enroll(change, hero, method)
    }

    suspend fun submitApplication(heroId: String, guildId: String): GuildMine {
        val method = "guildApply"
        val hero = requireFree(heroId, method)
        val change = Change(requireGuild(guildId, method)).also { it.maintain() }
        val guild = change.guild
        if (guild.mode != GuildMode.APPLY) throw GuildExceptions.funExceptionMode(method, guild.mode.name)
        requireLevel(guild, hero, method)
        if (guild.applications.any { it.heroId == heroId }) throw GuildExceptions.funExceptionApplied(method, guild.name)
        guild.applications += GuildApplication(heroId, change.now)
        change.save(method)
        return outside(hero)
    }

    suspend fun acceptApplication(heroId: String, applicantId: String): GuildView {
        val method = "guildAcceptApplication"
        val change = acting(heroId, method, staff = true)
        val guild = change.guild
        if (guild.applications.none { it.heroId == applicantId }) throw GuildExceptions.funExceptionApplication(method, applicantId)
        val applicant = heroes.requireHero(applicantId, method)
        val elsewhere = guildOf(applicantId)
        if (elsewhere != null) {
            guild.applications.removeAll { it.heroId == applicantId }
            change.save(method)
            throw GuildExceptions.funExceptionAlreadyMember(method, elsewhere.name)
        }
        enroll(change, applicant, method)
        return view(change, change.record(heroId))
    }

    suspend fun declineApplication(heroId: String, applicantId: String): GuildView {
        val method = "guildDeclineApplication"
        val change = acting(heroId, method, staff = true)
        if (!change.guild.applications.removeAll { it.heroId == applicantId }) throw GuildExceptions.funExceptionApplication(method, applicantId)
        change.save(method)
        return view(change, change.record(heroId))
    }

    /** Приглашение по имени героя - в любом режиме гильдии. */
    suspend fun invite(heroId: String, name: String): GuildView {
        val method = "guildInvite"
        val change = acting(heroId, method, staff = true)
        val guild = change.guild
        val target = heroes.findByField(Hero::name, name.trim()) ?: throw GuildExceptions.funExceptionHeroName(method, name)
        guildOf(target._id)?.let { throw GuildExceptions.funExceptionAlreadyMember(method, it.name) }
        if (guild.invites.any { it.heroId == target._id }) throw GuildExceptions.funExceptionInvited(method, target.name)
        guild.invites += GuildInvite(target._id, change.actor.name, change.now)
        change.save(method)
        return view(change, change.record(heroId))
    }

    suspend fun acceptInvite(heroId: String, guildId: String): GuildMine {
        val method = "guildAcceptInvite"
        val hero = requireFree(heroId, method)
        val change = Change(requireGuild(guildId, method)).also { it.maintain() }
        if (change.guild.invites.none { it.heroId == heroId }) throw GuildExceptions.funExceptionInvite(method, guildId)
        return enroll(change, hero, method)
    }

    suspend fun declineInvite(heroId: String, guildId: String): GuildMine {
        val method = "guildDeclineInvite"
        val hero = heroes.requireHero(heroId, method)
        val change = Change(requireGuild(guildId, method)).also { it.maintain() }
        if (!change.guild.invites.removeAll { it.heroId == heroId }) throw GuildExceptions.funExceptionInvite(method, guildId)
        change.save(method)
        return outside(hero)
    }

    /** Выход: глава уходит только последним - тогда гильдия распускается. */
    suspend fun leave(heroId: String): GuildMine {
        val method = "guildLeave"
        val change = acting(heroId, method)
        val me = change.record(heroId)
        if (me.role == GuildRole.LEADER) {
            if (change.guild.members.size > 1) throw GuildExceptions.funExceptionLeaderLeaves(method, change.guild.name)
            return disband(heroId)
        }
        change.discharge(change.actor, GuildLogKind.LEFT, "")
        change.save(method)
        return outside(change.actor)
    }

    /** Исключение: глава - любого, офицер - только участника. */
    suspend fun kick(heroId: String, memberId: String): GuildView {
        val method = "guildKick"
        val change = acting(heroId, method, staff = true)
        val target = change.target(memberId, method)
        val me = change.record(heroId)
        if (target.role == GuildRole.LEADER || (me.role == GuildRole.OFFICER && target.role != GuildRole.MEMBER)) throw GuildExceptions.funExceptionRights(method, me.role.name)
        change.discharge(heroes.requireHero(memberId, method), GuildLogKind.KICKED, change.actor.name)
        change.save(method)
        return view(change, me)
    }

    suspend fun promote(heroId: String, memberId: String): GuildView {
        val method = "guildPromote"
        val change = acting(heroId, method, leader = true)
        val target = change.target(memberId, method)
        if (target.role != GuildRole.MEMBER) throw GuildExceptions.funExceptionTarget(method, memberId)
        if (change.guild.count(GuildRole.OFFICER) >= rules.officers) throw GuildExceptions.funExceptionOfficers(method, rules.officers.toString())
        change.assign(target, GuildRole.OFFICER)
        change.log(GuildLogKind.PROMOTED, change.nameOf(memberId), GuildRole.OFFICER.name)
        change.save(method)
        return view(change, change.record(heroId))
    }

    suspend fun demote(heroId: String, memberId: String): GuildView {
        val method = "guildDemote"
        val change = acting(heroId, method, leader = true)
        val target = change.target(memberId, method)
        if (target.role != GuildRole.OFFICER) throw GuildExceptions.funExceptionTarget(method, memberId)
        change.assign(target, GuildRole.MEMBER)
        change.log(GuildLogKind.DEMOTED, change.nameOf(memberId), GuildRole.MEMBER.name)
        change.save(method)
        return view(change, change.record(heroId))
    }

    /** Передача главенства: прежний глава становится офицером, если есть место, иначе участником. */
    suspend fun transfer(heroId: String, memberId: String): GuildView {
        val method = "guildTransfer"
        val change = acting(heroId, method, leader = true)
        val target = change.target(memberId, method)
        change.handOver(change.record(heroId), target)
        change.save(method)
        return view(change, change.record(heroId))
    }

    /** Роспуск главой: гильдия, её журнал и чат удаляются, у героев состава пропадает гильдия; вступить можно сразу. */
    suspend fun disband(heroId: String): GuildMine {
        val method = "guildDisband"
        val change = acting(heroId, method, leader = true)
        val guild = change.guild
        transactionExecute("guild $method ${guild._id}") { session ->
            deleteById(guild, session)
            heroes.patchGuild(Filters.`in`("_id", guild.members.map { it.heroId }), Updates.unset("guild"), session)
            events.deleteByGuild(guild._id, session)
            chats.deleteByGuild(guild._id, session)
        }
        return outside(heroes.requireHero(heroId, method))
    }

    suspend fun settings(heroId: String, mode: GuildMode?, minLevel: Int?, emblem: String?, color: String?, announcement: String?): GuildView {
        val method = "guildSettings"
        val change = acting(heroId, method, leader = true)
        val guild = change.guild
        if (emblem != null || color != null) requireLook(emblem ?: guild.emblem, color ?: guild.color, method)
        minLevel?.let { requireMinLevel(it, method) }
        val text = announcement?.trim()
        if (text != null && text.length > rules.announcement) throw GuildExceptions.funExceptionAnnouncement(method, rules.announcement.toString())
        mode?.let { guild.mode = it }
        minLevel?.let { guild.minLevel = it }
        emblem?.let { guild.emblem = it }
        color?.let { guild.color = it }
        text?.let { guild.announcement = it }
        change.save(method)
        return view(change, change.record(heroId))
    }

    // ==================== ВКЛАД ====================

    /**
     * Вклад золотом или сферами: всё уходит в казну, золотая стоимость - в опыт гильдии, личный вклад
     * (ранг), неделю и суточный потолок; за стоимость герой получает знаки гильдии.
     */
    suspend fun contribute(heroId: String, item: String, amount: Long): GuildContribution {
        val method = "guildContribute"
        if (amount <= 0) throw GuildExceptions.funExceptionAmount(method, amount.toString())
        val change = acting(heroId, method)
        val hero = change.actor
        val guild = change.guild
        val me = change.record(heroId)
        val gold = item.equals(GOLD, ignoreCase = true)
        val value = if (gold) amount else {
            val orb = index.item(item)?.takeIf { it.category == Item.CURRENCY && it.price > 0 } ?: throw GuildExceptions.funExceptionNotOrb(method, item)
            Math.multiplyExact(orb.price, amount)
        }
        val today = day(change.now)
        if (me.day != today) { me.day = today; me.dayContribution = 0 }
        val left = (rules.dailyLimit(hero.level) - me.dayContribution).coerceAtLeast(0)
        if (value > left) throw GuildExceptions.funExceptionDailyLimit(method, left.toString())
        if (gold) {
            if (hero.money < amount) throw CharacterExceptions.funExceptionGold(method, amount.toString())
            hero.pay(amount)
            guild.treasuryGold += amount
        } else {
            hero.spend(item, amount, method)
            guild.treasuryOrbs.merge(item, amount, Long::plus)
        }
        val rankBefore = rules.rankIndex(me.contribution)
        me.contribution += value
        me.dayContribution += value
        val week = week(change.now)
        if (me.week != week) { me.week = week; me.weekContribution = 0 }
        me.weekContribution += value
        change.touch(hero)
        change.log(GuildLogKind.CONTRIBUTED, hero.name, "$amount ${if (gold) GOLD else item}")
        val rankAfter = rules.rankIndex(me.contribution)
        if (rankAfter > rankBefore) change.log(GuildLogKind.RANK_UP, hero.name, rules.ranks[rankAfter].code)
        val grown = change.grow(value, hero.name)
        change.sync(hero)
        commit(change, method, grown)
        val cards = heroes.cards(guild.members.map { it.heroId })
        return GuildContribution(view(change, me, cards), member(me, cards[heroId], change.now), hero.money)
    }

    // ==================== ЗАДАНИЯ ====================

    /** Задания гильдии героя: его личные на сутки и общие цели с вкладом каждого участника. */
    suspend fun quests(heroId: String): GuildQuests {
        val method = "guildQuests"
        val change = acting(heroId, method)
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
        val change = acting(heroId, method)
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
        commit(change, method, change.grown)
        return questsView(change, hero)
    }

    /**
     * Сдать все гильдейские разом (1.22.0, из «сдать всё» заданий): выполненные личные и долю в каждой закрытой общей
     * цели, где вклад героя не ниже порога. Вне гильдии - ничего.
     */
    suspend fun claimAllQuests(heroId: String, method: String): List<QuestClaimed> {
        if (guildOf(heroId) == null) return emptyList()
        val change = acting(heroId, method)
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
        commit(change, method, change.grown)
        return claimed
    }

    /** Личное гильдейское: награда на героя, опыт гильдии - за каждое. */
    private fun settle(change: Change, hero: Hero, quest: Quest) {
        questService.grant(hero, quest.reward)
        quest.claimed = true
        change.grow(quest.reward.guildExperience, hero.name)
    }

    /** Доля общей цели: награда по уровню героя; опыт гильдии - один раз, первым, кто забрал долю. */
    private fun settle(change: Change, hero: Hero, log: GuildQuestLog, target: GuildGoal): QuestReward {
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

    private suspend fun questsView(change: Change, hero: Hero): GuildQuests {
        val board = change.guild.quests
        val tallies = tallies(change, hero)
        fun view(goal: GuildGoal): GuildGoalView {
            val shares = shares(tallies, change.guild, goal)
            return GuildGoalView(goal, shares.values.sum(), shares[hero._id] ?: 0, need(change.guild, goal),
                goal.key in hero.quests.guild?.claimed.orEmpty(), questService.sharedReward(hero, goal), shares.filterValues { it > 0 })
        }
        return GuildQuests(hero.quests.guild?.quests.orEmpty(), board.daily.map(::view), board.weekly.map(::view),
            QuestClock.dayEnd(change.now), QuestClock.weekEnd(change.now), hero.money)
    }

    /** Порог доли: [GuildQuestRule.fairShare] средней доли участника, не меньше единицы. */
    private fun need(guild: Guild, goal: GuildGoal): Long =
        kotlin.math.ceil(goal.target.toDouble() / guild.members.size.coerceAtLeast(1) * index.quests.guild.fairShare).toLong().coerceAtLeast(1)

    private suspend fun shares(change: Change, hero: Hero, goal: GuildGoal): Map<String, Long> = shares(tallies(change, hero), change.guild, goal)

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
    private suspend fun tallies(change: Change, hero: Hero): Map<String, GuildQuestLog> =
        heroes.guildTallies(change.guild.members.map { it.heroId }.filter { it != hero._id }) + listOfNotNull(hero.quests.guild?.let { hero._id to it })

    // ==================== ЧАТ ====================

    suspend fun say(heroId: String, text: String): GuildMessage {
        val method = "guildChatPost"
        val change = acting(heroId, method)
        val body = text.trim()
        if (body.isEmpty() || body.length > rules.chat.length) throw GuildExceptions.funExceptionChatText(method, rules.chat.length.toString())
        val cooldown = rules.chat.cooldownSeconds * 1000L
        chats.lastAt(change.guild._id, heroId)?.let { last ->
            val wait = last + cooldown - change.now
            if (wait > 0) throw GuildExceptions.funExceptionChatCooldown(method, ((wait + 999) / 1000).toString())
        }
        val message = GuildChat(change.guild._id, change.now, heroId, change.actor.name, body)
        transactionExecute("guild $method ${change.guild._id}") { session ->
            change.write(session, quiet = true)
            chats.post(message, rules.chat.keep, session)
        }
        return message(message)
    }

    /** Глава и офицеры убирают сообщение из чата своей гильдии. */
    suspend fun unsay(heroId: String, messageId: String): GuildMessage {
        val method = "guildChatDelete"
        val change = acting(heroId, method, staff = true)
        val message = chats.findById(messageId)?.takeIf { it.guildId == change.guild._id } ?: throw GuildExceptions.funExceptionMessage(method, messageId)
        transactionExecute("guild $method ${change.guild._id}") { session ->
            change.write(session, quiet = true)
            chats.deleteById(message, session)
        }
        return message(message)
    }

    // ==================== ГЕРОЙ УДАЛЁН ====================

    /** Удалённый герой уходит из гильдии той же транзакцией; последний участник уносит гильдию с собой. */
    suspend fun forget(hero: Hero, session: ClientSession) {
        forgetPending(hero._id, null, session)
        val guild = collection.find(session, readFilter(Filters.eq("members.heroId", hero._id))).firstOrNull() ?: return
        if (guild.members.size == 1) {
            deleteById(guild, session)
            events.deleteByGuild(guild._id, session)
            chats.deleteByGuild(guild._id, session)
            return
        }
        val change = Change(guild).also { it.actor = hero }
        val me = change.record(hero._id)
        if (me.role == GuildRole.LEADER) successor(guild, hero._id)?.let { change.handOver(me, it) }
        guild.members.remove(me)
        change.log(GuildLogKind.LEFT, hero.name, "")
        update(guild, session)
        if (change.journal.isNotEmpty()) events.insertMany(change.journal, session)
    }

    // ==================== ВНУТРЕННЕЕ ====================

    /**
     * Правка одной гильдии: время команды, журнал, герои, чьи копии гильдии надо записать.
     * Записывается одной транзакцией [write].
     */
    private inner class Change(val guild: Guild) {
        val now: Long = System.currentTimeMillis()
        val journal = mutableListOf<GuildEvent>()
        private val touched = LinkedHashMap<String, Hero>()
        private val names = HashMap<String, String>()
        private var dirty = false
        lateinit var actor: Hero

        fun record(heroId: String): GuildMemberRecord = guild.member(heroId) ?: throw GuildExceptions.funExceptionNotMember("guild", heroId)

        fun target(memberId: String, method: String): GuildMemberRecord {
            if (memberId == actor._id) throw GuildExceptions.funExceptionTarget(method, memberId)
            return guild.member(memberId) ?: throw GuildExceptions.funExceptionMember(method, memberId)
        }

        fun log(kind: GuildLogKind, heroName: String, value: String) {
            journal += GuildEvent(guild._id, now, kind, heroName, value)
            dirty = true
        }

        suspend fun nameOf(heroId: String): String =
            touched[heroId]?.name ?: (if (::actor.isInitialized && actor._id == heroId) actor.name else null) ?: names[heroId]
                ?: heroes.cards(listOf(heroId))[heroId]?.name.orEmpty().also { names[heroId] = it }

        /** Участник заходил: отметка не чаще [SEEN_STEP], затем обслуживание гильдии и сверка копии героя. */
        suspend fun visit(hero: Hero) {
            actor = hero
            val me = record(hero._id)
            if (now - me.lastSeenAt >= SEEN_STEP) { me.lastSeenAt = now; dirty = true }
            maintain()
            plan()
            sync(hero)
        }

        /** Глава не заходил дольше `leaderIdleDays` - главенство старшему офицеру, иначе самому щедрому участнику. */
        suspend fun maintain() {
            val leader = guild.leader
            if (leader != null && now - leader.lastSeenAt < rules.leaderIdleDays * DAY) return
            val heir = successor(guild, leader?.heroId) ?: return
            if (leader != null) handOver(leader, heir, demoteTo = GuildRole.MEMBER) else { assign(heir, GuildRole.LEADER); log(GuildLogKind.LEADER_CHANGED, nameOf(heir.heroId), "") }
        }

        suspend fun handOver(from: GuildMemberRecord, to: GuildMemberRecord, demoteTo: GuildRole? = null) {
            assign(to, GuildRole.LEADER)
            assign(from, demoteTo ?: if (guild.count(GuildRole.OFFICER) < rules.officers) GuildRole.OFFICER else GuildRole.MEMBER)
            log(GuildLogKind.LEADER_CHANGED, nameOf(to.heroId), nameOf(from.heroId))
        }

        fun assign(record: GuildMemberRecord, role: GuildRole) {
            record.role = role
            record.roleAt = now
            dirty = true
        }

        /** Герой входит в состав: место, копия гильдии у героя, заявка и приглашение сюда закрыты. */
        fun enlist(hero: Hero, role: GuildRole) {
            guild.members += GuildMemberRecord(hero._id, role, joinedAt = now, roleAt = now, lastSeenAt = now)
            guild.applications.removeAll { it.heroId == hero._id }
            guild.invites.removeAll { it.heroId == hero._id }
            if (!::actor.isInitialized) actor = hero
            touched[hero._id] = hero
            sync(hero)
            dirty = true
        }

        /** Герой уходит из состава: копия гильдии снята, вступить снова - через `rejoinHours`. */
        fun discharge(hero: Hero, kind: GuildLogKind, by: String) {
            guild.members.removeAll { it.heroId == hero._id }
            hero.guild = null
            hero.guildLeftAt = now
            touched[hero._id] = hero
            log(kind, hero.name, by)
        }

        /** Копия гильдии у героя - как в документе гильдии. */
        fun sync(hero: Hero) {
            val me = guild.member(hero._id) ?: return
            val expected = HeroGuild(guild._id, guild.level, rules.rankIndex(me.contribution))
            if (hero.guild != expected) { hero.guild = expected; touched[hero._id] = hero }
        }

        fun touch(hero: Hero) { touched[hero._id] = hero }

        /** Уровень гильдии вырос за эту команду: копии уровня у участников надо переписать. */
        var grown = false
            private set

        /** Опыт гильдии; вырос уровень - запись в журнал и true: копии уровня у участников надо переписать. */
        fun grow(value: Long, heroName: String): Boolean {
            guild.experience += value
            val level = rules.levelFor(guild.experience)
            if (level <= guild.level) return false
            guild.level = level
            log(GuildLogKind.LEVEL_UP, heroName, level.toString())
            grown = true
            return true
        }

        /** Общие цели на новые сутки и неделю: цель растёт с числом участников на момент выдачи. */
        fun plan() {
            val board = guild.quests
            val day = QuestClock.day(now)
            val week = QuestClock.week(now)
            val dice = Dice.system()
            if (board.day != day) {
                board.day = day
                board.daily = questService.sharedGoals(QuestKind.GUILD_DAILY, QuestService.dayKey(day), guild.members.size, dice)
                dirty = true
            }
            if (board.week != week) {
                board.week = week
                board.weekly = questService.sharedGoals(QuestKind.GUILD_WEEKLY, QuestService.weekKey(week), guild.members.size, dice)
                dirty = true
            }
            if (board.paid.removeIf { !it.startsWith(QuestService.dayKey(day)) && !it.startsWith(QuestService.weekKey(week)) }) dirty = true
        }

        /**
         * Гильдия, тронутые герои и журнал. [fresh] - гильдия только что вставлена; [quiet] - команда гильдию
         * не меняла (чат), и она пишется, только если её тронуло обслуживание.
         */
        suspend fun write(session: ClientSession, fresh: Boolean = false, quiet: Boolean = false) {
            if (!fresh && (!quiet || dirty)) update(guild, session)
            touched.values.forEach { heroes.update(it, session) }
            if (journal.isNotEmpty()) events.insertMany(journal, session)
        }

        suspend fun save(method: String) {
            transactionExecute("guild $method ${guild._id}") { session -> write(session) }
        }

        /** Запись после чтения: не вышла (гонка с командой) - не беда, следующее чтение повторит. */
        suspend fun saveQuietly(method: String) {
            if (!dirty && touched.isEmpty()) return
            try {
                transactionExecute("guild $method ${guild._id}") { session -> write(session, quiet = true) }
            } catch (e: BaseException) {
                printLog("[Guild] $method ${guild._id}: ${e.message}")
            }
        }
    }

    /** Запись команды; вырос уровень гильдии - он же в копии у каждого участника. */
    private suspend fun commit(change: Change, method: String, grown: Boolean) {
        val guild = change.guild
        transactionExecute("guild $method ${guild._id}") { session ->
            change.write(session)
            if (grown) heroes.patchGuild(Filters.and(Filters.eq("guild.id", guild._id), Filters.ne("_id", change.actor._id)), Updates.set("guild.level", guild.level), session)
        }
    }

    /** Гильдия героя-участника с обслуживанием и сверкой; [staff] - только глава и офицеры, [leader] - только глава. */
    private suspend fun acting(heroId: String, method: String, staff: Boolean = false, leader: Boolean = false): Change {
        val hero = heroes.requireHero(heroId, method)
        val guild = guildOf(heroId) ?: run { heal(hero); throw GuildExceptions.funExceptionNotMember(method, heroId) }
        val change = Change(guild)
        change.visit(hero)
        val role = change.record(heroId).role
        if (leader && role != GuildRole.LEADER || staff && role == GuildRole.MEMBER) throw GuildExceptions.funExceptionRights(method, role.name)
        return change
    }

    /** Чтение гильдии героем: отметка захода пишется без обязательств. */
    private suspend fun reading(heroId: String, method: String): Change {
        val change = acting(heroId, method)
        change.saveQuietly(method)
        return change
    }

    /** Приём героя в гильдию: места, копия гильдии, заявки и приглашения героя в другие гильдии сняты. */
    private suspend fun enroll(change: Change, hero: Hero, method: String): GuildMine {
        val guild = change.guild
        val capacity = rules.capacity(guild.level)
        if (guild.members.size >= capacity) throw GuildExceptions.funExceptionFull(method, capacity.toString())
        change.enlist(hero, GuildRole.MEMBER)
        change.log(GuildLogKind.JOINED, hero.name, "")
        transactionExecute("guild $method ${guild._id}") { session ->
            change.write(session)
            forgetPending(hero._id, guild._id, session)
        }
        return inside(change, hero)
    }

    /** Заявки и приглашения героя во все гильдии, кроме [except], снимаются. */
    private suspend fun forgetPending(heroId: String, except: String?, session: ClientSession) {
        val pending = Filters.or(Filters.eq("applications.heroId", heroId), Filters.eq("invites.heroId", heroId))
        val filter = except?.let { Filters.and(pending, Filters.ne("_id", it)) } ?: pending
        collection.updateMany(
            session, readFilter(filter),
            Updates.combine(Updates.pull("applications", Document("heroId", heroId)), Updates.pull("invites", Document("heroId", heroId)), Updates.inc("version", 1L)),
        )
    }

    /** Копия гильдии у героя, которого в ней уже нет (распущена, исключён мимо героя), снимается. */
    private suspend fun heal(hero: Hero) {
        if (hero.guild != null) {
            hero.guild = null
            runCatching { heroes.save(hero, "guildHeal") }
        }
    }

    private suspend fun guildOf(heroId: String): Guild? = findByFilter(Filters.eq("members.heroId", heroId)).firstOrNull()

    private suspend fun requireGuild(guildId: String, method: String): Guild = findById(guildId) ?: throw GuildExceptions.funExceptionNotFound(method, guildId)

    /** Герой вне гильдии и не ждёт конца запрета на вступление. */
    private suspend fun requireFree(heroId: String, method: String): Hero {
        val hero = heroes.requireHero(heroId, method)
        guildOf(heroId)?.let { throw GuildExceptions.funExceptionAlreadyMember(method, it.name) }
        heal(hero)
        rejoinAt(hero)?.let { throw GuildExceptions.funExceptionRejoin(method, ((it - System.currentTimeMillis()) / MINUTE + 1).toString()) }
        return hero
    }

    private fun requireLevel(guild: Guild, hero: Hero, method: String) {
        if (hero.level < guild.minLevel) throw GuildExceptions.funExceptionMinLevel(method, guild.minLevel.toString())
    }

    private suspend fun requireName(name: String, method: String): String {
        val title = name.trim().replace(SPACES, " ")
        if (title.length !in rules.name[0]..rules.name[1]) throw GuildExceptions.funExceptionName(method, "${rules.name[0]}-${rules.name[1]}")
        if (findByField(Guild::nameKey, title.lowercase()) != null) throw GuildExceptions.funExceptionNameTaken(method, title)
        return title
    }

    private suspend fun requireTag(tag: String, method: String): String {
        val code = tag.trim().uppercase()
        if (code.length !in rules.tag[0]..rules.tag[1] || !code.all(Char::isLetterOrDigit)) throw GuildExceptions.funExceptionTag(method, "${rules.tag[0]}-${rules.tag[1]}")
        if (findByField(Guild::tag, code) != null) throw GuildExceptions.funExceptionTagTaken(method, code)
        return code
    }

    private fun requireLook(emblem: String, color: String, method: String) {
        if (emblem !in rules.emblems) throw GuildExceptions.funExceptionEmblem(method, emblem)
        if (rules.colors.none { it.equals(color, ignoreCase = true) }) throw GuildExceptions.funExceptionEmblem(method, color)
    }

    private fun requireMinLevel(minLevel: Int, method: String) {
        if (minLevel !in 1..MAX_MIN_LEVEL) throw GuildExceptions.funExceptionMinLevelValue(method, minLevel.toString())
    }

    private fun rejoinAt(hero: Hero): Long? =
        (hero.guildLeftAt + rules.rejoinHours * HOUR).takeIf { hero.guildLeftAt > 0 && it > System.currentTimeMillis() }

    /** Наследник главы: старший по назначению офицер, иначе участник с наибольшим вкладом. */
    private fun successor(guild: Guild, leaderId: String?): GuildMemberRecord? {
        val others = guild.members.filter { it.heroId != leaderId }
        return others.filter { it.role == GuildRole.OFFICER }.minByOrNull { it.roleAt }
            ?: others.maxWithOrNull(compareBy<GuildMemberRecord> { it.contribution }.thenByDescending { it.joinedAt })
    }

    private suspend fun outside(hero: Hero): GuildMine {
        val invited = findByFilter(Filters.eq("invites.heroId", hero._id))
        val invites = invited.mapNotNull { guild -> guild.invites.firstOrNull { it.heroId == hero._id }?.let { GuildInviteView(card(guild), it.by, it.at) } }
        return GuildMine(invites = invites, rejoinAt = rejoinAt(hero))
    }

    private suspend fun inside(change: Change, hero: Hero): GuildMine {
        val cards = heroes.cards(change.guild.members.map { it.heroId } + change.guild.applications.map { it.heroId })
        val me = change.record(hero._id)
        return GuildMine(view(change, me, cards), member(me, cards[hero._id], change.now))
    }

    private suspend fun view(change: Change, viewer: GuildMemberRecord): GuildView =
        view(change, viewer, heroes.cards(change.guild.members.map { it.heroId } + change.guild.applications.map { it.heroId }))

    private fun view(change: Change, viewer: GuildMemberRecord, cards: Map<String, HeroCard>): GuildView {
        val guild = change.guild
        val staff = viewer.role != GuildRole.MEMBER
        val members = guild.members.map { member(it, cards[it.heroId], change.now) }
            .sortedWith(compareBy<GuildMember> { it.role.ordinal }.thenByDescending { it.contribution })
        val applicants = if (!staff) emptyList() else guild.applications.mapNotNull { application ->
            cards[application.heroId]?.let { GuildApplicant(it.id, it.name, it.heroClass, it.level, application.at) }
        }
        return GuildView(
            guild._id, guild.name, guild.tag, guild.emblem, guild.color, guild.faction, guild.level, guild.experience, rules.next(guild.level),
            rules.capacity(guild.level), guild.mode, guild.minLevel, guild.announcement, guild.treasuryGold, guild.treasuryOrbs.toMap(),
            members, applicants, members.filter { it.weekContribution > 0 }.associate { it.heroId to it.weekContribution },
        )
    }

    private fun member(record: GuildMemberRecord, card: HeroCard?, now: Long): GuildMember = GuildMember(
        record.heroId, card?.name.orEmpty(), card?.heroClass.orEmpty(), card?.level ?: 1, record.role, record.contribution,
        if (record.week == week(now)) record.weekContribution else 0, rules.rankFor(record.contribution).code, record.joinedAt, record.lastSeenAt,
    )

    private fun card(guild: Guild) = GuildCard(
        guild._id, guild.name, guild.tag, guild.emblem, guild.color, guild.faction, guild.level, guild.members.size, rules.capacity(guild.level), guild.mode, guild.minLevel,
    )

    private fun message(chat: GuildChat) = GuildMessage(chat._id, chat.at, chat.heroId, chat.heroName, chat.text)

    private companion object {
        const val GOLD = "GOLD"
        const val MINUTE = 60_000L
        const val HOUR = 3_600_000L
        const val DAY = 86_400_000L
        /** Отметка захода пишется не чаще раза в столько: чтение не должно переписывать гильдию на каждый опрос чата. */
        const val SEEN_STEP = 10 * MINUTE
        const val MAX_MIN_LEVEL = 1000
        val SPACES = Regex("\\s+")

        fun day(now: Long): Long = now / DAY

        /** Неделя с понедельника (UTC): 1 января 1970 - четверг. */
        fun week(now: Long): Long = (day(now) + 3) / 7
    }
}

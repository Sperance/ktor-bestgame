package ru.descend.exileforge.features.logic.guild
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.sperance.exileforge.rules.content.GuildRole
import com.sperance.exileforge.rules.content.GuildRules
import ru.descend.exileforge.base.exception.model.GuildExceptions
import ru.descend.exileforge.config.ContentStore
import ru.descend.exileforge.config.MongoFactory.transactionExecute
import ru.descend.exileforge.features.data.guild.Guild
import ru.descend.exileforge.features.data.guild.GuildApplicant
import ru.descend.exileforge.features.data.guild.GuildCard
import ru.descend.exileforge.features.data.guild.GuildEventRepository
import ru.descend.exileforge.features.data.guild.GuildInviteView
import ru.descend.exileforge.features.data.guild.GuildMember
import ru.descend.exileforge.features.data.guild.GuildMemberRecord
import ru.descend.exileforge.features.data.guild.GuildMine
import ru.descend.exileforge.features.data.guild.GuildRepository
import ru.descend.exileforge.features.data.guild.GuildView
import ru.descend.exileforge.features.data.hero.Hero
import ru.descend.exileforge.features.data.hero.HeroCard
import ru.descend.exileforge.features.data.hero.HeroRepository
import ru.descend.exileforge.features.logic.quests.QuestService

/**
 * Общее для сервисов гильдии: гильдия героя с обслуживанием и сверкой копии, запись команды одной
 * транзакцией, снимки гильдии и участника для ответов. Любое чтение и запись гильдии сначала обслуживают её:
 * отмечают, что участник заходил, и передают главенство, если глава не заходил дольше `leaderIdleDays`.
 */
class GuildAccess(
    private val guilds: GuildRepository,
    private val heroes: HeroRepository,
    private val events: GuildEventRepository,
    private val content: ContentStore,
    private val questService: QuestService,
) {
    private val rules: GuildRules get() = content.index.guilds

    fun change(guild: Guild): GuildChange = GuildChange(guild, guilds, heroes, events, content, questService)

    /** Гильдия героя-участника с обслуживанием и сверкой; [staff] - только глава и офицеры, [leader] - только глава. */
    suspend fun acting(heroId: String, method: String, staff: Boolean = false, leader: Boolean = false): GuildChange {
        val hero = heroes.requireHero(heroId, method)
        val guild = guilds.byMember(heroId) ?: run {
            heal(hero)
            throw GuildExceptions.funExceptionNotMember(method, heroId)
        }
        val change = change(guild)
        change.visit(hero)
        val role = change.record(heroId).role
        if (leader && role != GuildRole.LEADER || staff && role == GuildRole.MEMBER) throw GuildExceptions.funExceptionRights(method, role.name)
        return change
    }

    /** Чтение гильдии героем: отметка захода пишется без обязательств. */
    suspend fun reading(heroId: String, method: String): GuildChange {
        val change = acting(heroId, method)
        change.saveQuietly(method)
        return change
    }

    /** Запись команды; вырос уровень гильдии - он же в копии у каждого участника. */
    suspend fun commit(change: GuildChange, method: String, grown: Boolean) {
        val guild = change.guild
        transactionExecute("guild $method ${guild._id}") { session ->
            change.write(session)
            if (grown) heroes.patchGuild(Filters.and(Filters.eq("guild.id", guild._id), Filters.ne("_id", change.actor._id)), Updates.set("guild.level", guild.level), session)
        }
    }

    /** Копия гильдии у героя, которого в ней уже нет (распущена, исключён мимо героя), снимается. */
    suspend fun heal(hero: Hero) {
        if (hero.guild != null) {
            hero.guild = null
            runCatching { heroes.save(hero, "guildHeal") }
        }
    }

    fun rejoinAt(hero: Hero): Long? = (hero.guildLeftAt + rules.rejoinHours * GuildClock.HOUR).takeIf { hero.guildLeftAt > 0 && it > System.currentTimeMillis() }

    suspend fun outside(hero: Hero): GuildMine {
        val invited = guilds.inviting(hero._id)
        val invites = invited.mapNotNull { guild -> guild.invites.firstOrNull { it.heroId == hero._id }?.let { GuildInviteView(card(guild), it.by, it.at) } }
        return GuildMine(invites = invites, rejoinAt = rejoinAt(hero))
    }

    suspend fun inside(change: GuildChange, hero: Hero): GuildMine {
        val cards = heroes.cards(change.guild.members.map { it.heroId } + change.guild.applications.map { it.heroId })
        val me = change.record(hero._id)
        return GuildMine(view(change, me, cards), member(me, cards[hero._id], change.now))
    }

    suspend fun view(change: GuildChange, viewer: GuildMemberRecord): GuildView = view(change, viewer, heroes.cards(change.guild.members.map { it.heroId } + change.guild.applications.map { it.heroId }))

    fun view(change: GuildChange, viewer: GuildMemberRecord, cards: Map<String, HeroCard>): GuildView {
        val guild = change.guild
        val staff = viewer.role != GuildRole.MEMBER
        val members = guild.members.map { member(it, cards[it.heroId], change.now) }
            .sortedWith(compareBy<GuildMember> { it.role.ordinal }.thenByDescending { it.contribution })
        val applicants = if (!staff) {
            emptyList()
        } else {
            guild.applications.mapNotNull { application ->
                cards[application.heroId]?.let { GuildApplicant(it.id, it.name, it.heroClass, it.level, application.at) }
            }
        }
        return GuildView(
            guild._id, guild.name, guild.tag, guild.emblem, guild.color, guild.faction, guild.level, guild.experience, rules.next(guild.level),
            rules.capacity(guild.level), guild.mode, guild.minLevel, guild.announcement, guild.treasuryGold, guild.treasuryOrbs.toMap(),
            members, applicants, members.filter { it.weekContribution > 0 }.associate { it.heroId to it.weekContribution },
            guild.tree.toMap(), (rules.tree.points(guild.level) - rules.tree.spent(guild.tree)).coerceAtLeast(0), guild.treeResetAt + rules.tree.respecDays * GuildClock.DAY,
        )
    }

    fun member(record: GuildMemberRecord, card: HeroCard?, now: Long): GuildMember = GuildMember(
        record.heroId, card?.name.orEmpty(), card?.heroClass.orEmpty(), card?.level ?: 1, record.role, record.contribution,
        if (record.week == GuildClock.week(now)) record.weekContribution else 0, rules.rankFor(record.contribution).code, record.joinedAt, record.lastSeenAt,
    )

    fun card(guild: Guild) = GuildCard(
        guild._id, guild.name, guild.tag, guild.emblem, guild.color, guild.faction, guild.level, guild.members.size, rules.capacity(guild.level), guild.mode, guild.minLevel,
    )
}

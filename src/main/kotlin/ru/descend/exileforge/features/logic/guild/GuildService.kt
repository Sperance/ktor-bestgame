package ru.descend.exileforge.features.logic.guild
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.sperance.exileforge.rules.content.GuildLogKind
import com.sperance.exileforge.rules.content.GuildMode
import com.sperance.exileforge.rules.content.GuildRole
import com.sperance.exileforge.rules.content.GuildRules
import ru.descend.exileforge.base.exception.model.CharacterExceptions
import ru.descend.exileforge.base.exception.model.GuildExceptions
import ru.descend.exileforge.base.route.PagedMongoResponse
import ru.descend.exileforge.config.ContentStore
import ru.descend.exileforge.config.MongoFactory.transactionExecute
import ru.descend.exileforge.features.data.guild.Guild
import ru.descend.exileforge.features.data.guild.GuildApplication
import ru.descend.exileforge.features.data.guild.GuildCard
import ru.descend.exileforge.features.data.guild.GuildEventRepository
import ru.descend.exileforge.features.data.guild.GuildInvite
import ru.descend.exileforge.features.data.guild.GuildLogEntry
import ru.descend.exileforge.features.data.guild.GuildMine
import ru.descend.exileforge.features.data.guild.GuildRepository
import ru.descend.exileforge.features.data.guild.GuildView
import ru.descend.exileforge.features.data.hero.Hero
import ru.descend.exileforge.features.data.hero.HeroRepository

/**
 * Гильдии (1.20.0): основание, состав, заявки и приглашения, роли, роспуск и настройки. Правда о составе -
 * документ гильдии; у героя лежит копия [ru.descend.exileforge.features.data.hero.HeroGuild], и каждое чтение
 * гильдии героем сверяет её ([GuildAccess]).
 */
class GuildService(
    private val guilds: GuildRepository,
    private val heroes: HeroRepository,
    private val events: GuildEventRepository,
    private val content: ContentStore,
    private val access: GuildAccess,
) {
    private val rules: GuildRules get() = content.index.guilds

    // ==================== ЧТЕНИЕ ====================

    /** Гильдия героя глазами героя; вне гильдии - приглашения и когда можно вступить снова. */
    suspend fun mine(heroId: String): GuildMine {
        val hero = heroes.requireHero(heroId, "guildMine")
        val guild = guilds.byMember(heroId)
        if (guild == null) {
            access.heal(hero)
            return access.outside(hero)
        }
        val change = access.change(guild)
        change.visit(hero)
        change.saveQuietly("mine")
        return access.inside(change, hero)
    }

    /** Поиск по имени или тегу [text]; [faction] - только гильдии этой фракции. Порядок - по опыту (рейтинг). */
    suspend fun search(heroId: String, text: String?, faction: String?, page: Int, size: Int): PagedMongoResponse<GuildCard> {
        heroes.requireHero(heroId, "guildSearch")
        val found = guilds.search(text, faction, page, size)
        return PagedMongoResponse(found.items.map(access::card), found.page, found.totalItems, found.totalPages)
    }

    suspend fun log(heroId: String, page: Int, size: Int): List<GuildLogEntry> {
        val change = access.reading(heroId, "guildLog")
        return events.page(change.guild._id, page, size)
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
        val change = access.change(guild)
        change.enlist(hero, GuildRole.LEADER)
        change.log(GuildLogKind.CREATED, hero.name, title)
        transactionExecute("guild $method") { session ->
            guilds.insert(guild, session)
            change.write(session, fresh = true)
            guilds.forgetPending(hero._id, guild._id, session)
        }
        return access.inside(change, hero)
    }

    /** Вступление в открытую гильдию. */
    suspend fun join(heroId: String, guildId: String): GuildMine {
        val method = "guildJoin"
        val hero = requireFree(heroId, method)
        val change = access.change(requireGuild(guildId, method)).also { it.maintain() }
        if (change.guild.mode != GuildMode.OPEN) throw GuildExceptions.funExceptionMode(method, change.guild.mode.name)
        requireLevel(change.guild, hero, method)
        return enroll(change, hero, method)
    }

    suspend fun submitApplication(heroId: String, guildId: String): GuildMine {
        val method = "guildApply"
        val hero = requireFree(heroId, method)
        val change = access.change(requireGuild(guildId, method)).also { it.maintain() }
        val guild = change.guild
        if (guild.mode != GuildMode.APPLY) throw GuildExceptions.funExceptionMode(method, guild.mode.name)
        requireLevel(guild, hero, method)
        if (guild.applications.any { it.heroId == heroId }) throw GuildExceptions.funExceptionApplied(method, guild.name)
        guild.applications += GuildApplication(heroId, change.now)
        change.save(method)
        return access.outside(hero)
    }

    suspend fun acceptApplication(heroId: String, applicantId: String): GuildView {
        val method = "guildAcceptApplication"
        val change = access.acting(heroId, method, staff = true)
        val guild = change.guild
        if (guild.applications.none { it.heroId == applicantId }) throw GuildExceptions.funExceptionApplication(method, applicantId)
        val applicant = heroes.requireHero(applicantId, method)
        val elsewhere = guilds.byMember(applicantId)
        if (elsewhere != null) {
            guild.applications.removeAll { it.heroId == applicantId }
            change.save(method)
            throw GuildExceptions.funExceptionAlreadyMember(method, elsewhere.name)
        }
        enroll(change, applicant, method)
        return access.view(change, change.record(heroId))
    }

    suspend fun declineApplication(heroId: String, applicantId: String): GuildView {
        val method = "guildDeclineApplication"
        val change = access.acting(heroId, method, staff = true)
        if (!change.guild.applications.removeAll { it.heroId == applicantId }) throw GuildExceptions.funExceptionApplication(method, applicantId)
        change.save(method)
        return access.view(change, change.record(heroId))
    }

    /** Приглашение по имени героя - в любом режиме гильдии. */
    suspend fun invite(heroId: String, name: String): GuildView {
        val method = "guildInvite"
        val change = access.acting(heroId, method, staff = true)
        val guild = change.guild
        val target = heroes.findByField(Hero::name, name.trim()) ?: throw GuildExceptions.funExceptionHeroName(method, name)
        guilds.byMember(target._id)?.let { throw GuildExceptions.funExceptionAlreadyMember(method, it.name) }
        if (guild.invites.any { it.heroId == target._id }) throw GuildExceptions.funExceptionInvited(method, target.name)
        guild.invites += GuildInvite(target._id, change.actor.name, change.now)
        change.save(method)
        return access.view(change, change.record(heroId))
    }

    suspend fun acceptInvite(heroId: String, guildId: String): GuildMine {
        val method = "guildAcceptInvite"
        val hero = requireFree(heroId, method)
        val change = access.change(requireGuild(guildId, method)).also { it.maintain() }
        if (change.guild.invites.none { it.heroId == heroId }) throw GuildExceptions.funExceptionInvite(method, guildId)
        return enroll(change, hero, method)
    }

    suspend fun declineInvite(heroId: String, guildId: String): GuildMine {
        val method = "guildDeclineInvite"
        val hero = heroes.requireHero(heroId, method)
        val change = access.change(requireGuild(guildId, method)).also { it.maintain() }
        if (!change.guild.invites.removeAll { it.heroId == heroId }) throw GuildExceptions.funExceptionInvite(method, guildId)
        change.save(method)
        return access.outside(hero)
    }

    /** Выход: глава уходит только последним - тогда гильдия распускается. */
    suspend fun leave(heroId: String): GuildMine {
        val method = "guildLeave"
        val change = access.acting(heroId, method)
        val me = change.record(heroId)
        if (me.role == GuildRole.LEADER) {
            if (change.guild.members.size > 1) throw GuildExceptions.funExceptionLeaderLeaves(method, change.guild.name)
            return disband(heroId)
        }
        change.discharge(change.actor, GuildLogKind.LEFT, "")
        change.save(method)
        return access.outside(change.actor)
    }

    /** Исключение: глава - любого, офицер - только участника. */
    suspend fun kick(heroId: String, memberId: String): GuildView {
        val method = "guildKick"
        val change = access.acting(heroId, method, staff = true)
        val target = change.target(memberId, method)
        val me = change.record(heroId)
        if (target.role == GuildRole.LEADER || (me.role == GuildRole.OFFICER && target.role != GuildRole.MEMBER)) throw GuildExceptions.funExceptionRights(method, me.role.name)
        change.discharge(heroes.requireHero(memberId, method), GuildLogKind.KICKED, change.actor.name)
        change.save(method)
        return access.view(change, me)
    }

    suspend fun promote(heroId: String, memberId: String): GuildView {
        val method = "guildPromote"
        val change = access.acting(heroId, method, leader = true)
        val target = change.target(memberId, method)
        if (target.role != GuildRole.MEMBER) throw GuildExceptions.funExceptionTarget(method, memberId)
        if (change.guild.count(GuildRole.OFFICER) >= rules.officers) throw GuildExceptions.funExceptionOfficers(method, rules.officers.toString())
        change.assign(target, GuildRole.OFFICER)
        change.log(GuildLogKind.PROMOTED, change.nameOf(memberId), GuildRole.OFFICER.name)
        change.save(method)
        return access.view(change, change.record(heroId))
    }

    suspend fun demote(heroId: String, memberId: String): GuildView {
        val method = "guildDemote"
        val change = access.acting(heroId, method, leader = true)
        val target = change.target(memberId, method)
        if (target.role != GuildRole.OFFICER) throw GuildExceptions.funExceptionTarget(method, memberId)
        change.assign(target, GuildRole.MEMBER)
        change.log(GuildLogKind.DEMOTED, change.nameOf(memberId), GuildRole.MEMBER.name)
        change.save(method)
        return access.view(change, change.record(heroId))
    }

    /** Передача главенства: прежний глава становится офицером, если есть место, иначе участником. */
    suspend fun transfer(heroId: String, memberId: String): GuildView {
        val method = "guildTransfer"
        val change = access.acting(heroId, method, leader = true)
        val target = change.target(memberId, method)
        change.handOver(change.record(heroId), target)
        change.save(method)
        return access.view(change, change.record(heroId))
    }

    /**
     * Роспуск главой: гильдия и её журнал удаляются, у героев состава пропадает гильдия. С 1.30.0 роспуск - тот же
     * выход: `guildLeftAt` ставится всему составу, и новая гильдия - только через `rejoinHours`, иначе роспуском
     * перебирали бы гильдейские задания.
     */
    suspend fun disband(heroId: String): GuildMine {
        val method = "guildDisband"
        val change = access.acting(heroId, method, leader = true)
        val guild = change.guild
        transactionExecute("guild $method ${guild._id}") { session ->
            guilds.deleteById(guild, session)
            heroes.patchGuild(
                Filters.`in`("_id", guild.members.map { it.heroId }),
                Updates.combine(Updates.unset("guild"), Updates.set("guildLeftAt", System.currentTimeMillis())),
                session,
            )
            events.deleteByGuild(guild._id, session)
        }
        return access.outside(heroes.requireHero(heroId, method))
    }

    suspend fun settings(heroId: String, mode: GuildMode?, minLevel: Int?, emblem: String?, color: String?, announcement: String?): GuildView {
        val method = "guildSettings"
        val change = access.acting(heroId, method, leader = true)
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
        return access.view(change, change.record(heroId))
    }

    // ==================== ГЕРОЙ УДАЛЁН ====================

    /** Удалённый герой уходит из гильдии той же транзакцией; последний участник уносит гильдию с собой. */
    suspend fun forget(hero: Hero, session: ClientSession) {
        guilds.forgetPending(hero._id, null, session)
        val guild = guilds.byMember(hero._id, session) ?: return
        if (guild.members.size == 1) {
            guilds.deleteById(guild, session)
            events.deleteByGuild(guild._id, session)
            return
        }
        val change = access.change(guild).also { it.actor = hero }
        val me = change.record(hero._id)
        if (me.role == GuildRole.LEADER) GuildChange.successor(guild, hero._id)?.let { change.handOver(me, it) }
        guild.members.remove(me)
        change.log(GuildLogKind.LEFT, hero.name, "")
        guilds.update(guild, session)
        if (change.journal.isNotEmpty()) events.insertMany(change.journal, session)
    }

    // ==================== ВНУТРЕННЕЕ ====================

    /** Приём героя в гильдию: места, копия гильдии, заявки и приглашения героя в другие гильдии сняты. */
    private suspend fun enroll(change: GuildChange, hero: Hero, method: String): GuildMine {
        val guild = change.guild
        val capacity = rules.capacity(guild.level)
        if (guild.members.size >= capacity) throw GuildExceptions.funExceptionFull(method, capacity.toString())
        change.enlist(hero, GuildRole.MEMBER)
        change.log(GuildLogKind.JOINED, hero.name, "")
        transactionExecute("guild $method ${guild._id}") { session ->
            change.write(session)
            guilds.forgetPending(hero._id, guild._id, session)
        }
        return access.inside(change, hero)
    }

    private suspend fun requireGuild(guildId: String, method: String): Guild = guilds.find(guildId) ?: throw GuildExceptions.funExceptionNotFound(method, guildId)

    /** Герой вне гильдии и не ждёт конца запрета на вступление. */
    private suspend fun requireFree(heroId: String, method: String): Hero {
        val hero = heroes.requireHero(heroId, method)
        guilds.byMember(heroId)?.let { throw GuildExceptions.funExceptionAlreadyMember(method, it.name) }
        access.heal(hero)
        access.rejoinAt(hero)?.let { throw GuildExceptions.funExceptionRejoin(method, ((it - System.currentTimeMillis()) / GuildClock.MINUTE + 1).toString()) }
        return hero
    }

    private fun requireLevel(guild: Guild, hero: Hero, method: String) {
        if (hero.level < guild.minLevel) throw GuildExceptions.funExceptionMinLevel(method, guild.minLevel.toString())
    }

    private suspend fun requireName(name: String, method: String): String {
        val title = name.trim().replace(SPACES, " ")
        if (title.length !in rules.name[0]..rules.name[1]) throw GuildExceptions.funExceptionName(method, "${rules.name[0]}-${rules.name[1]}")
        if (guilds.findByField(Guild::nameKey, title.lowercase()) != null) throw GuildExceptions.funExceptionNameTaken(method, title)
        return title
    }

    private suspend fun requireTag(tag: String, method: String): String {
        val code = tag.trim().uppercase()
        if (code.length !in rules.tag[0]..rules.tag[1] || !code.all(Char::isLetterOrDigit)) throw GuildExceptions.funExceptionTag(method, "${rules.tag[0]}-${rules.tag[1]}")
        if (guilds.findByField(Guild::tag, code) != null) throw GuildExceptions.funExceptionTagTaken(method, code)
        return code
    }

    private fun requireLook(emblem: String, color: String, method: String) {
        if (emblem !in rules.emblems) throw GuildExceptions.funExceptionEmblem(method, emblem)
        if (rules.colors.none { it.equals(color, ignoreCase = true) }) throw GuildExceptions.funExceptionEmblem(method, color)
    }

    private fun requireMinLevel(minLevel: Int, method: String) {
        if (minLevel !in 1..MAX_MIN_LEVEL) throw GuildExceptions.funExceptionMinLevelValue(method, minLevel.toString())
    }

    private companion object {
        const val MAX_MIN_LEVEL = 1000
        val SPACES = Regex("\\s+")
    }
}

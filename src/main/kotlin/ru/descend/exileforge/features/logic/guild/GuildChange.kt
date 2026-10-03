package ru.descend.exileforge.features.logic.guild
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.sperance.exileforge.rules.content.GuildLogKind
import com.sperance.exileforge.rules.content.GuildRole
import com.sperance.exileforge.rules.content.GuildRules
import com.sperance.exileforge.rules.content.QuestClock
import com.sperance.exileforge.rules.content.QuestKind
import com.sperance.exileforge.rules.roll.Dice
import ru.descend.exileforge.base.exception.BaseException
import ru.descend.exileforge.base.exception.model.GuildExceptions
import ru.descend.exileforge.config.ContentStore
import ru.descend.exileforge.config.MongoFactory.transactionExecute
import ru.descend.exileforge.extensions.printLog
import ru.descend.exileforge.features.data.guild.Guild
import ru.descend.exileforge.features.data.guild.GuildEvent
import ru.descend.exileforge.features.data.guild.GuildEventRepository
import ru.descend.exileforge.features.data.guild.GuildMemberRecord
import ru.descend.exileforge.features.data.guild.GuildRepository
import ru.descend.exileforge.features.data.hero.Hero
import ru.descend.exileforge.features.data.hero.HeroGuild
import ru.descend.exileforge.features.data.hero.HeroRepository
import ru.descend.exileforge.features.logic.quests.QuestService

/** Время гильдии: сутки и недели UTC для вклада, взятий и обслуживания. */
internal object GuildClock {
    const val MINUTE = 60_000L
    const val HOUR = 3_600_000L
    const val DAY = 86_400_000L

    /** Отметка захода пишется не чаще раза в столько: чтение не должно переписывать гильдию на каждый опрос. */
    const val SEEN_STEP = 10 * MINUTE

    fun day(now: Long): Long = now / DAY

    /** Неделя с понедельника (UTC): 1 января 1970 - четверг. */
    fun week(now: Long): Long = (day(now) + 3) / 7
}

/**
 * Правка одной гильдии: время команды, журнал, герои, чьи копии гильдии надо записать.
 * Записывается одной транзакцией [write].
 */
class GuildChange(
    val guild: Guild,
    private val guilds: GuildRepository,
    private val heroes: HeroRepository,
    private val events: GuildEventRepository,
    private val content: ContentStore,
    private val questService: QuestService,
) {
    private val rules: GuildRules get() = content.index.guilds
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

    suspend fun nameOf(heroId: String): String = touched[heroId]?.name ?: (if (::actor.isInitialized && actor._id == heroId) actor.name else null) ?: names[heroId]
        ?: heroes.cards(listOf(heroId))[heroId]?.name.orEmpty().also { names[heroId] = it }

    /** Участник заходил: отметка не чаще [GuildClock.SEEN_STEP], затем обслуживание гильдии и сверка копии героя. */
    suspend fun visit(hero: Hero) {
        actor = hero
        val me = record(hero._id)
        if (now - me.lastSeenAt >= GuildClock.SEEN_STEP) {
            me.lastSeenAt = now
            dirty = true
        }
        maintain()
        plan()
        sync(hero)
    }

    /** Глава не заходил дольше `leaderIdleDays` - главенство старшему офицеру, иначе самому щедрому участнику. */
    suspend fun maintain() {
        val leader = guild.leader
        if (leader != null && now - leader.lastSeenAt < rules.leaderIdleDays * GuildClock.DAY) return
        val heir = successor(guild, leader?.heroId) ?: return
        if (leader != null) {
            handOver(leader, heir, demoteTo = GuildRole.MEMBER)
        } else {
            assign(heir, GuildRole.LEADER)
            log(GuildLogKind.LEADER_CHANGED, nameOf(heir.heroId), "")
        }
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
        val expected = HeroGuild(guild._id, guild.level, rules.rankIndex(me.contribution), rules.tree.effects(guild.tree))
        if (hero.guild != expected) {
            hero.guild = expected
            touched[hero._id] = hero
        }
    }

    fun touch(hero: Hero) {
        touched[hero._id] = hero
    }

    /** Строка древа гильдии по ключу (1.74.0). */
    fun effect(key: String): Double = rules.tree.effects(guild.tree)[key] ?: 0.0

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
     * не меняла, и она пишется, только если её тронуло обслуживание.
     */
    suspend fun write(session: ClientSession, fresh: Boolean = false, quiet: Boolean = false) {
        if (!fresh && (!quiet || dirty)) guilds.update(guild, session)
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

    companion object {
        /** Наследник главы: старший по назначению офицер, иначе участник с наибольшим вкладом. */
        fun successor(guild: Guild, leaderId: String?): GuildMemberRecord? {
            val others = guild.members.filter { it.heroId != leaderId }
            return others.filter { it.role == GuildRole.OFFICER }.minByOrNull { it.roleAt }
                ?: others.maxWithOrNull(compareBy<GuildMemberRecord> { it.contribution }.thenByDescending { it.joinedAt })
        }
    }
}

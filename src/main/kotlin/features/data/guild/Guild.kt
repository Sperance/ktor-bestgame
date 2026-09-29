package features.data.guild

import base.entity.StockEntity
import base.entity.VersionedEntity
import com.sperance.exileforge.rules.content.GuildLogKind
import com.sperance.exileforge.rules.content.GuildMode
import com.sperance.exileforge.rules.content.GuildQuestBoard
import com.sperance.exileforge.rules.content.GuildRole
import extensions.now
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

/**
 * Участник в документе гильдии. Имя, класс и уровень героя здесь не хранятся - их читает состав из героев.
 * [week]/[day] - номер недели и суток (UTC), к которым относятся [weekContribution]/[dayContribution];
 * [roleAt] - когда назначена роль: старший офицер - тот, кто офицер дольше всех.
 */
@Serializable
data class GuildMemberRecord(
    val heroId: String,
    var role: GuildRole = GuildRole.MEMBER,
    var contribution: Long = 0,
    var week: Long = 0,
    var weekContribution: Long = 0,
    var day: Long = 0,
    var dayContribution: Long = 0,
    val joinedAt: Long = 0,
    var roleAt: Long = 0,
    var lastSeenAt: Long = 0,
)

/** Заявка героя в гильдию режима APPLY. */
@Serializable
data class GuildApplication(val heroId: String, val at: Long)

/** Приглашение героя; [by] - имя пригласившего. */
@Serializable
data class GuildInvite(val heroId: String, val by: String, val at: Long)

/**
 * Гильдия (1.20.0) - коллекция `Guild`: состав, заявки и приглашения внутри документа (их не больше
 * потолка состава), журнал и чат - своими коллекциями [GuildEvent] и [GuildChat]. Название уникально без
 * учёта регистра ([nameKey]), тег хранится заглавными и тоже уникален. Фракция (1.25.0) не меняется никогда.
 */
@Serializable
data class Guild(
    var name: String,
    var nameKey: String = name.lowercase(),
    var tag: String,
    val faction: String,
    var emblem: String,
    var color: String,
    var mode: GuildMode = GuildMode.OPEN,
    var minLevel: Int = 1,
    var announcement: String = "",
    var experience: Long = 0,
    var level: Int = 1,
    var treasuryGold: Long = 0,
    var treasuryOrbs: MutableMap<String, Long> = mutableMapOf(),
    var members: MutableList<GuildMemberRecord> = mutableListOf(),
    var applications: MutableList<GuildApplication> = mutableListOf(),
    var invites: MutableList<GuildInvite> = mutableListOf(),
    /** Общие цели гильдии на сутки и неделю (1.21.0): выдаются сами при первом обращении в новых сутках. */
    var quests: GuildQuestBoard = GuildQuestBoard(),
    override var _id: String = ObjectId().toHexString(),
    override var version: Long = 0,
    override var deleted: Boolean = false,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override var updatedAt: LocalDateTime = LocalDateTime.now(),
) : VersionedEntity {

    fun member(heroId: String): GuildMemberRecord? = members.firstOrNull { it.heroId == heroId }

    val leader: GuildMemberRecord? get() = members.firstOrNull { it.role == GuildRole.LEADER }

    fun count(role: GuildRole): Int = members.count { it.role == role }
}

/** Запись журнала гильдии: [heroName] - о ком она, [value] - подробность (сумма с кодом, уровень, ранг, имя). */
@Serializable
data class GuildEvent(
    val guildId: String,
    val at: Long,
    val kind: GuildLogKind,
    val heroName: String,
    val value: String = "",
    override var _id: String = ObjectId().toHexString(),
) : StockEntity

/** Сообщение чата гильдии; хранится не больше `chat.keep` последних на гильдию. */
@Serializable
data class GuildChat(
    val guildId: String,
    val at: Long,
    val heroId: String,
    val heroName: String,
    val text: String,
    override var _id: String = ObjectId().toHexString(),
) : StockEntity

// ==================== ОТВЕТЫ ====================

@Serializable
data class GuildMember(
    val heroId: String,
    val name: String,
    val heroClass: String,
    val level: Int,
    val role: GuildRole,
    val contribution: Long,
    val weekContribution: Long,
    val rank: String,
    val joinedAt: Long,
    val lastSeenAt: Long,
)

@Serializable
data class GuildApplicant(val heroId: String, val name: String, val heroClass: String, val level: Int, val at: Long)

/**
 * Гильдия глазами участника. [next] - опыт, с которого начнётся следующий уровень (0 - потолок);
 * [applications] видят только глава и офицеры; [weekly] - вклад участников за текущую неделю по id героя.
 */
@Serializable
data class GuildView(
    val id: String,
    val name: String,
    val tag: String,
    val emblem: String,
    val color: String,
    val faction: String,
    val level: Int,
    val experience: Long,
    val next: Long,
    val capacity: Int,
    val mode: GuildMode,
    val minLevel: Int,
    val announcement: String,
    val treasuryGold: Long,
    val treasuryOrbs: Map<String, Long>,
    val members: List<GuildMember>,
    val applications: List<GuildApplicant>,
    val weekly: Map<String, Long>,
)

/** Строка поиска гильдий. */
@Serializable
data class GuildCard(
    val id: String,
    val name: String,
    val tag: String,
    val emblem: String,
    val color: String,
    val faction: String,
    val level: Int,
    val members: Int,
    val capacity: Int,
    val mode: GuildMode,
    val minLevel: Int,
)

/** Приглашение героя в гильдию: сама гильдия карточкой, кто и когда пригласил. */
@Serializable
data class GuildInviteView(val guild: GuildCard, val by: String, val at: Long)

/** Всё о гильдии героя: сама гильдия, он в ней, приглашения и когда можно вступить снова (мс эпохи; null - сейчас). */
@Serializable
data class GuildMine(val guild: GuildView? = null, val me: GuildMember? = null, val invites: List<GuildInviteView> = emptyList(), val rejoinAt: Long? = null)

@Serializable
data class GuildContribution(val guild: GuildView, val me: GuildMember, val money: Long)

@Serializable
data class GuildLogEntry(val at: Long, val kind: GuildLogKind, val heroName: String, val value: String)

@Serializable
data class GuildMessage(val id: String, val at: Long, val heroId: String, val heroName: String, val text: String)

/** Тело `POST /guild/chat`. */
@Serializable
data class GuildChatBody(val text: String)

/** Тело `POST /guild/settings`, если объявление пришло не параметром. */
@Serializable
data class GuildSettingsBody(val announcement: String? = null)

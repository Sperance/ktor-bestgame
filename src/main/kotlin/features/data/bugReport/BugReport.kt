package features.data.bugReport

import base.entity.VersionedEntity
import extensions.now
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

/**
 * Что стало с отчётом (1.69.0 - статусы окна администратора): [NEW] - новое, [IN_PROGRESS] - в работе (на реализацию),
 * [DONE] - реализовано или исправлено, [WONTFIX] - закрыто; закрытое предложение из общего списка уходит.
 */
@Serializable
enum class BugStatus { NEW, IN_PROGRESS, DONE, WONTFIX }

/** Вид отчёта (1.69.0): ошибка или предложение игрока. */
@Serializable
enum class FeedbackKind { BUG, SUGGESTION }

/** Голос игрока за предложение (1.69.0): один на аккаунт, переключаемый. */
@Serializable
enum class Vote { LIKE, DISLIKE, NONE }

/**
 * Отчёт игрока (1.46.0), как его прислала кнопка «Жучок»: текст, где он был ([screen] и [context] - вкладка, окно, герой,
 * зона, версии, устройство) и хвост журнала запросов клиента без токенов. [userId] - аккаунт живой сессии, если игрок вошёл;
 * [address] - адрес отправителя. С 1.69.0 это и предложение ([kind]): за него голосуют [likes] и [dislikes] (id аккаунтов),
 * [rating] - их разность для сортировки; [reason] - слово администратора при смене статуса.
 */
@Serializable
data class BugReport(
    val text: String = "",
    val screen: String = "",
    val context: Map<String, String> = emptyMap(),
    val requests: List<String> = emptyList(),
    val userId: String? = null,
    val address: String = "",
    var status: BugStatus = BugStatus.NEW,
    val kind: FeedbackKind = FeedbackKind.BUG,
    var likes: MutableList<String> = mutableListOf(),
    var dislikes: MutableList<String> = mutableListOf(),
    var rating: Int = 0,
    var reason: String = "",

    override var _id: String = ObjectId().toHexString(),
    override var version: Long = 0,
    override var deleted: Boolean = false,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override var updatedAt: LocalDateTime = LocalDateTime.now(),
) : VersionedEntity {
    /** Голосовать можно за открытое предложение, и не автору. */
    fun votable(by: String): Boolean = kind == FeedbackKind.SUGGESTION && userId != by && status in OPEN

    /** Предложение в общем списке (1.69.0): без автора - его видит только администратор. */
    fun toPublic(viewer: String) = SuggestionView(_id, text, status, likes.size, dislikes.size,
        when (viewer) { in likes -> Vote.LIKE; in dislikes -> Vote.DISLIKE; else -> Vote.NONE }, userId == viewer, createdAt.toString())

    /** Своё - автору (1.69.0): вид, статус и слово администратора. */
    fun toOwn() = OwnReport(_id, kind, text, status, reason, likes.size, dislikes.size, createdAt.toString())

    companion object { val OPEN = setOf(BugStatus.NEW, BugStatus.IN_PROGRESS) }
}

@Serializable
data class SuggestionView(val id: String, val text: String, val status: BugStatus, val likes: Int, val dislikes: Int, val vote: Vote, val mine: Boolean, val createdAt: String)

@Serializable
data class OwnReport(val id: String, val kind: FeedbackKind, val text: String, val status: BugStatus, val reason: String, val likes: Int, val dislikes: Int, val createdAt: String)

/** Отчёт целиком для администратора (1.69.0): с автором. */
@Serializable
data class AdminReport(val report: BugReport, val login: String?)

/** Тело отчёта с клиента; пределы - в [BugReportRepository]. */
@Serializable
data class BugReportRequest(val text: String, val screen: String = "", val context: Map<String, String> = emptyMap(), val requests: List<String> = emptyList(),
    val kind: FeedbackKind = FeedbackKind.BUG)

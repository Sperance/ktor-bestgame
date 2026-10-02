package features.data.bugReport

import base.exception.BaseException
import base.repository.BaseRepository
import base.repository.IndexSpec
import com.mongodb.client.model.Filters
import config.ContentStore
import config.MongoFactory.transactionExecute
import features.data.mail.MailRepository
import features.data.user.UserRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Отчёты игроков (1.46.0): ошибки и с 1.69.0 предложения. Предложения видят все вошедшие - без автора, за них голосуют
 * лайком или дизлайком; автор видит свои отчёты со статусом; администратор - всё с авторами, меняет статус, и автор
 * получает об этом письмо.
 */
class BugReportRepository : BaseRepository<BugReport>(entityClass = BugReport::class), KoinComponent {
    private val mail: MailRepository by inject()
    private val users: UserRepository by inject()
    private val content: ContentStore by inject()
    private val export: FeedbackExport by inject()

    override val indexes = listOf(IndexSpec.on("status", "createdAt"), IndexSpec.on("createdAt"), IndexSpec.on("kind", "status", "rating"), IndexSpec.on("userId"))

    /** Сохраняет отчёт, обрезав всё сверх пределов: пустой текст - отказ `BUG_001`, предложение без входа - `BUG_002`. */
    suspend fun file(request: BugReportRequest, userId: String?, address: String): String {
        val limits = content.index.rules.inputs
        val text = request.text.trim().take(if (request.kind == FeedbackKind.SUGGESTION) limits.suggestion else limits.report)
        if (text.isEmpty()) throw BaseException("Bug report text is empty", "BugReport", "file", "BUG_001")
        if (request.kind == FeedbackKind.SUGGESTION && userId == null) throw BaseException("A suggestion needs an account", "BugReport", "file", "BUG_002")
        val context = request.context.entries.take(MAX_CONTEXT_KEYS).associate { (key, value) -> key.take(MAX_KEY) to value.take(MAX_VALUE) }
        val report = BugReport(text, request.screen.take(MAX_KEY), context, request.requests.takeLast(MAX_REQUESTS).map { it.take(MAX_VALUE) }, userId, address,
            kind = request.kind)
        return transactionExecute("bug report") { session -> insert(report, session) }._id
    }

    /** Общий список предложений: всё, кроме закрытых, по рейтингу (лайки минус дизлайки), затем новые первыми. */
    suspend fun suggestions(viewer: String): List<SuggestionView> =
        findByFilter(Filters.and(Filters.eq("kind", FeedbackKind.SUGGESTION.name), Filters.ne("status", BugStatus.WONTFIX.name)))
            .sortedWith(compareByDescending<BugReport> { it.rating }.thenByDescending { it.createdAt }).map { it.toPublic(viewer) }

    /** Голос [vote] аккаунта [viewer] за предложение [id]: один на аккаунт, повтор того же снимает; своё и закрытое - `BUG_003`. */
    suspend fun vote(viewer: String, id: String, vote: Vote): SuggestionView {
        val report = findById(id)?.takeIf { it.kind == FeedbackKind.SUGGESTION } ?: throw BaseException("No suggestion $id", "BugReport", "vote", "BUG_004", listOf(id))
        if (!report.votable(viewer)) throw BaseException("Suggestion $id cannot be voted on", "BugReport", "vote", "BUG_003", listOf(id))
        report.likes.remove(viewer)
        report.dislikes.remove(viewer)
        when (vote) {
            Vote.LIKE -> report.likes += viewer
            Vote.DISLIKE -> report.dislikes += viewer
            Vote.NONE -> Unit
        }
        report.rating = report.likes.size - report.dislikes.size
        transactionExecute("vote $id") { session -> update(report, session) }
        return report.toPublic(viewer)
    }

    /** Свои отчёты аккаунта, новые первыми. */
    suspend fun mine(userId: String): List<OwnReport> = findByFilter(Filters.eq("userId", userId)).sortedByDescending { it.createdAt }.map { it.toOwn() }

    /** Всё для администратора: вид и статус - фильтры (null - любые), с логином автора. */
    suspend fun all(kind: FeedbackKind?, status: BugStatus?): List<AdminReport> {
        val filter = Filters.and(listOfNotNull(kind?.let { Filters.eq("kind", it.name) }, status?.let { Filters.eq("status", it.name) }).ifEmpty { listOf(Filters.exists("_id")) })
        val reports = findByFilter(filter).sortedWith(compareByDescending<BugReport> { it.rating }.thenByDescending { it.createdAt })
        val logins = reports.mapNotNull { it.userId }.distinct().associateWith { users.findById(it)?.login }
        return reports.map { AdminReport(it, it.userId?.let(logins::get)) }
    }

    /** Новый статус с причиной; автор, если он известен, получает письмо `mail.feedback_status`. */
    suspend fun setStatus(id: String, status: BugStatus, reason: String): AdminReport {
        val report = findById(id) ?: throw BaseException("No report $id", "BugReport", "setStatus", "BUG_004", listOf(id))
        val changed = report.status != status || report.reason != reason.trim()
        report.status = status
        report.reason = reason.trim().take(MAX_VALUE)
        transactionExecute("report status $id") { session -> update(report, session) }
        if (changed) report.userId?.let { author ->
            mail.system(author, MAIL_KEY, listOf(report.kind.name, status.name, report.text.take(EXCERPT), report.reason))
        }
        return AdminReport(report, report.userId?.let { users.findById(it)?.login })
    }

    /**
     * В Asana (1.70.0): задача с текстом и контекстом, ссылка на неё в отчёте и статус «в работе» (автору - письмо, как при
     * любой смене статуса). Уже выгруженный отчёт второй задачи не получает.
     */
    suspend fun exportToAsana(id: String): AdminReport {
        val report = findById(id) ?: throw BaseException("No report $id", "BugReport", "asana", "BUG_004", listOf(id))
        val login = report.userId?.let { users.findById(it)?.login }
        if (report.asanaUrl.isNotBlank()) return AdminReport(report, login)
        report.asanaUrl = export.export(report, login).ifBlank { ASANA_EXPORTED }
        transactionExecute("report asana $id") { session -> update(report, session) }
        return setStatus(id, BugStatus.IN_PROGRESS, report.reason)
    }

    private companion object {
        /** Задача создана, но Asana не вернула ссылку: отметка, что второй раз выгружать не надо. */
        const val ASANA_EXPORTED = "asana"
        const val MAX_CONTEXT_KEYS = 40
        const val MAX_KEY = 80
        const val MAX_VALUE = 400
        const val MAX_REQUESTS = 20
        const val EXCERPT = 120
        const val MAIL_KEY = "mail.feedback_status"
    }
}

package ru.descend.exileforge.features.logic.feedback

import com.mongodb.client.model.Filters
import ru.descend.exileforge.base.exception.BaseException
import ru.descend.exileforge.config.ContentStore
import ru.descend.exileforge.config.MongoFactory.transactionExecute
import ru.descend.exileforge.features.data.bugReport.AdminReport
import ru.descend.exileforge.features.data.bugReport.BugReport
import ru.descend.exileforge.features.data.bugReport.BugReportRepository
import ru.descend.exileforge.features.data.bugReport.BugReportRequest
import ru.descend.exileforge.features.data.bugReport.BugStatus
import ru.descend.exileforge.features.data.bugReport.FeedbackExport
import ru.descend.exileforge.features.data.bugReport.FeedbackKind
import ru.descend.exileforge.features.data.mail.MailRepository
import ru.descend.exileforge.features.data.user.UserRepository

/** Отчёты и предложения игроков: приём с пределами, работа администратора со статусом, письмо автору и выгрузка в Asana. */
class FeedbackService(
    private val reports: BugReportRepository,
    private val mails: MailRepository,
    private val users: UserRepository,
    private val content: ContentStore,
    private val export: FeedbackExport,
) {
    /** Сохраняет отчёт, обрезав всё сверх пределов: пустой текст - отказ `BUG_001`, предложение без входа - `BUG_002`. */
    suspend fun file(request: BugReportRequest, userId: String?, address: String): String {
        val limits = content.index.rules.inputs
        val text = request.text.trim().take(if (request.kind == FeedbackKind.SUGGESTION) limits.suggestion else limits.report)
        if (text.isEmpty()) throw BaseException("Bug report text is empty", "BugReport", "file", "BUG_001")
        if (request.kind == FeedbackKind.SUGGESTION && userId == null) throw BaseException("A suggestion needs an account", "BugReport", "file", "BUG_002")
        val context = request.context.entries.take(MAX_CONTEXT_KEYS).associate { (key, value) -> key.take(MAX_KEY) to value.take(MAX_VALUE) }
        val report = BugReport(
            text,
            request.screen.take(MAX_KEY),
            context,
            request.requests.takeLast(MAX_REQUESTS).map { it.take(MAX_VALUE) },
            userId,
            address,
            kind = request.kind,
        )
        return transactionExecute("bug report") { session -> reports.insert(report, session) }._id
    }

    /** Всё для администратора: вид и статус - фильтры (null - любые), с логином автора. */
    suspend fun all(kind: FeedbackKind?, status: BugStatus?): List<AdminReport> {
        val filter = Filters.and(listOfNotNull(kind?.let { Filters.eq("kind", it.name) }, status?.let { Filters.eq("status", it.name) }).ifEmpty { listOf(Filters.exists("_id")) })
        val found = reports.findByFilter(filter).sortedWith(compareByDescending<BugReport> { it.rating }.thenByDescending { it.createdAt })
        val logins = found.mapNotNull { it.userId }.distinct().associateWith { users.findById(it)?.login }
        return found.map { AdminReport(it, it.userId?.let(logins::get)) }
    }

    /** Новый статус с причиной; автор, если он известен, получает письмо `mail.feedback_status`. */
    suspend fun setStatus(id: String, status: BugStatus, reason: String): AdminReport {
        val report = reports.findById(id) ?: throw BaseException("No report $id", "BugReport", "setStatus", "BUG_004", listOf(id))
        val changed = report.status != status || report.reason != reason.trim()
        report.status = status
        report.reason = reason.trim().take(MAX_VALUE)
        transactionExecute("report status $id") { session -> reports.update(report, session) }
        if (changed) {
            report.userId?.let { author ->
                mails.system(author, MAIL_KEY, listOf(report.kind.name, status.name, report.text.take(EXCERPT), report.reason))
            }
        }
        return AdminReport(report, report.userId?.let { users.findById(it)?.login })
    }

    /**
     * В Asana (1.70.0): задача с текстом и контекстом, ссылка на неё в отчёте и статус «в работе» (автору - письмо, как при
     * любой смене статуса). Уже выгруженный отчёт второй задачи не получает.
     */
    suspend fun exportToAsana(id: String): AdminReport {
        val report = reports.findById(id) ?: throw BaseException("No report $id", "BugReport", "asana", "BUG_004", listOf(id))
        val login = report.userId?.let { users.findById(it)?.login }
        if (report.asanaUrl.isNotBlank()) return AdminReport(report, login)
        report.asanaUrl = export.export(report, login).ifBlank { ASANA_EXPORTED }
        transactionExecute("report asana $id") { session -> reports.update(report, session) }
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

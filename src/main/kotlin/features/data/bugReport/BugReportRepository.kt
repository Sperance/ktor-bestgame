package features.data.bugReport

import base.exception.BaseException
import base.repository.BaseRepository
import base.repository.IndexSpec
import config.MongoFactory.transactionExecute

/** Отчёты об ошибках (1.46.0): только запись; читает их владелец в Mongo. */
class BugReportRepository : BaseRepository<BugReport>(entityClass = BugReport::class) {

    override val indexes = listOf(IndexSpec.on("status", "createdAt"), IndexSpec.on("createdAt"))

    /** Сохраняет отчёт, обрезав всё сверх пределов: пустой текст - отказ `BUG_001`. */
    suspend fun file(request: BugReportRequest, userId: String?, address: String): String {
        val text = request.text.trim().take(MAX_TEXT)
        if (text.isEmpty()) throw BaseException("Bug report text is empty", "BugReport", "file", "BUG_001")
        val context = request.context.entries.take(MAX_CONTEXT_KEYS).associate { (key, value) -> key.take(MAX_KEY) to value.take(MAX_VALUE) }
        val report = BugReport(text, request.screen.take(MAX_KEY), context, request.requests.takeLast(MAX_REQUESTS).map { it.take(MAX_VALUE) }, userId, address)
        return transactionExecute("bug report") { session -> insert(report, session) }._id
    }

    private companion object {
        const val MAX_TEXT = 2000
        const val MAX_CONTEXT_KEYS = 40
        const val MAX_KEY = 80
        const val MAX_VALUE = 400
        const val MAX_REQUESTS = 20
    }
}

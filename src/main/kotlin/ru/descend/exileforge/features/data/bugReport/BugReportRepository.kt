package ru.descend.exileforge.features.data.bugReport

import com.mongodb.client.model.Filters
import ru.descend.exileforge.base.exception.BaseException
import ru.descend.exileforge.base.repository.BaseRepository
import ru.descend.exileforge.base.repository.IndexSpec
import ru.descend.exileforge.config.MongoFactory.transactionExecute

/**
 * Отчёты игроков (1.46.0): ошибки и с 1.69.0 предложения. Предложения видят все вошедшие - без автора, за них голосуют
 * лайком или дизлайком; автор видит свои отчёты со статусом; администратор - всё с авторами, меняет статус, и автор
 * получает об этом письмо.
 */
class BugReportRepository : BaseRepository<BugReport>(entityClass = BugReport::class) {
    override val indexes = listOf(IndexSpec.on("status", "createdAt"), IndexSpec.on("createdAt"), IndexSpec.on("kind", "status", "rating"), IndexSpec.on("userId"))

    /** Общий список предложений: всё, кроме закрытых, по рейтингу (лайки минус дизлайки), затем новые первыми. */
    suspend fun suggestions(viewer: String): List<SuggestionView> = findByFilter(Filters.and(Filters.eq("kind", FeedbackKind.SUGGESTION.name), Filters.ne("status", BugStatus.WONTFIX.name)))
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
}

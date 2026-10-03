package ru.descend.exileforge.features.data.bugReport
import io.ktor.http.HttpHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import ru.descend.exileforge.base.route.BaseRoute
import ru.descend.exileforge.base.route.Crud
import ru.descend.exileforge.base.route.queryParam
import ru.descend.exileforge.base.route.respondOk
import ru.descend.exileforge.features.data.auth.AuthSessionRepository
import ru.descend.exileforge.features.logic.auth.Tokens
import ru.descend.exileforge.server.addons.BUG_LIMIT

/**
 * Кнопка «Жучок» (1.46.0): `POST /api/v1/bugreport` - открыт и до входа, не больше [BUG_LIMIT] в час с аккаунта или
 * адреса. Сессия, если она есть, узнаётся здесь же: политика доступа этот маршрут не проверяет. Читает коллекцию
 * только администратор (общее чтение закрытой коллекции).
 */
class BugReportRoute(private val repo: BugReportRepository, private val service: ru.descend.exileforge.features.logic.feedback.FeedbackService, private val sessions: AuthSessionRepository) :
    BaseRoute<BugReport>(
        repository = repo,
        entitySerializer = BugReport.serializer(),
        operations = setOf(Crud.READ),
    ) {
    override fun additionalRoutes(route: Route) = with(route) {
        // Предложения игроков (1.69.0): общий список без авторов, голос, свои отчёты.
        get("/suggestions") { call.respondOk(repo.suggestions(ru.descend.exileforge.features.data.mail.me())) }
        get("/mine") { call.respondOk(repo.mine(ru.descend.exileforge.features.data.mail.me())) }
        post("/vote") {
            val vote = call.queryParam("vote").let { name -> Vote.entries.firstOrNull { it.name == name } }
                ?: throw ru.descend.exileforge.base.exception.BaseRouteExceptions.funExceptionQuery("vote", "vote")
            call.respondOk(repo.vote(ru.descend.exileforge.features.data.mail.me(), call.queryParam("id"), vote))
        }
        rateLimit(BUG_LIMIT) {
            post {
                val request = call.receive<BugReportRequest>()
                val userId = Tokens.fromHeader(call.request.headers[HttpHeaders.Authorization])?.let { sessions.resolve(it)?.userId }
                call.respondOk(service.file(request, userId, call.request.origin.remoteAddress))
            }
        }
    }
}

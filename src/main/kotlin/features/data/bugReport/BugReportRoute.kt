package features.data.bugReport

import base.route.BaseRoute
import base.route.Crud
import base.route.queryParam
import base.route.respondOk
import features.data.auth.AuthSessionRepository
import features.logic.auth.Tokens
import io.ktor.http.HttpHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import server.addons.BUG_LIMIT

/**
 * Кнопка «Жучок» (1.46.0): `POST /api/v1/bugreport` - открыт и до входа, не больше [BUG_LIMIT] в час с аккаунта или
 * адреса. Сессия, если она есть, узнаётся здесь же: политика доступа этот маршрут не проверяет. Читает коллекцию
 * только администратор (общее чтение закрытой коллекции).
 */
class BugReportRoute(private val repo: BugReportRepository, private val sessions: AuthSessionRepository) :
    BaseRoute<BugReport>(
        repository = repo,
        entitySerializer = BugReport.serializer(),
        operations = setOf(Crud.READ),
    ) {
    override fun additionalRoutes(route: Route) = with(route) {
        // Предложения игроков (1.69.0): общий список без авторов, голос, свои отчёты.
        get("/suggestions") { call.respondOk(repo.suggestions(features.data.mail.me())) }
        get("/mine") { call.respondOk(repo.mine(features.data.mail.me())) }
        post("/vote") {
            val vote = call.queryParam("vote").let { name -> Vote.entries.firstOrNull { it.name == name } }
                ?: throw base.exception.BaseRouteExceptions.funExceptionQuery("vote", "vote")
            call.respondOk(repo.vote(features.data.mail.me(), call.queryParam("id"), vote))
        }
        rateLimit(BUG_LIMIT) {
            post {
                val request = call.receive<BugReportRequest>()
                val userId = Tokens.fromHeader(call.request.headers[HttpHeaders.Authorization])?.let { sessions.resolve(it)?.userId }
                call.respondOk(repo.file(request, userId, call.request.origin.remoteAddress))
            }
        }
    }
}

package ru.descend.exileforge.features.data.admin
import io.ktor.server.request.receive
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import ru.descend.exileforge.base.exception.BaseRouteExceptions
import ru.descend.exileforge.base.route.RouteRegistrar
import ru.descend.exileforge.base.route.apiPath
import ru.descend.exileforge.base.route.optionalParam
import ru.descend.exileforge.base.route.queryParam
import ru.descend.exileforge.base.route.respondOk
import ru.descend.exileforge.features.data.auth.AuthSessionRepository
import ru.descend.exileforge.features.data.bugReport.BugReportRepository
import ru.descend.exileforge.features.data.bugReport.BugStatus
import ru.descend.exileforge.features.data.bugReport.FeedbackKind
import ru.descend.exileforge.features.data.mail.MailRepository
import ru.descend.exileforge.features.data.mail.MailRequest
import ru.descend.exileforge.features.data.user.UserRepository

/**
 * Окно аккаунта администратора (1.69.0): тестировщики - список, новый с логином и случайным паролем, новый пароль, отключение.
 * Весь путь `/api/v1/admin` закрыт политикой доступа для всех, кроме администратора.
 */
class AdminRoute(
    private val users: ru.descend.exileforge.features.logic.auth.UserService,
    private val sessions: AuthSessionRepository,
    private val reports: ru.descend.exileforge.features.logic.feedback.FeedbackService,
    private val mail: ru.descend.exileforge.features.logic.mail.MailService,
) : RouteRegistrar {
    override fun register(routing: Routing) {
        routing.route(apiPath("admin")) {
            // Отчёты и предложения (1.69.0): всё с авторами; смена статуса пишет автору письмо.
            route("/feedback") {
                get {
                    val kind = call.optionalParam("kind")?.let { name -> FeedbackKind.entries.firstOrNull { it.name == name } }
                    val status = call.optionalParam("status")?.let { name -> BugStatus.entries.firstOrNull { it.name == name } }
                    call.respondOk(reports.all(kind, status))
                }
                post("/status") {
                    val status = call.queryParam("status").let { name -> BugStatus.entries.firstOrNull { it.name == name } }
                        ?: throw BaseRouteExceptions.funExceptionQuery("feedbackStatus", "status")
                    call.respondOk(reports.setStatus(call.queryParam("id"), status, call.optionalParam("reason").orEmpty()))
                }
                // В Asana (1.70.0): задача из отчёта, ссылка в нём и статус «в работе».
                post("/asana") { call.respondOk(reports.exportToAsana(call.queryParam("id"))) }
            }
            // Письмо одному аккаунту или всем (1.69.0), с вложением или без.
            post("/mail") { call.respondOk(mail.send(call.receive<MailRequest>())) }
            route("/testers") {
                get { call.respondOk(users.testers()) }
                post { call.respondOk(users.createTester(call.queryParam("login"))) }
                post("/reset") {
                    val account = users.resetTester(call.queryParam("userId"))
                    sessions.revokeAll(account.id)
                    call.respondOk(account)
                }
                post("/active") {
                    val account = users.setTesterActive(call.queryParam("userId"), call.queryParam("active", true))
                    if (!account.active) sessions.revokeAll(account.id)
                    call.respondOk(account)
                }
            }
        }
    }
}

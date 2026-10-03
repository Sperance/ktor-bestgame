package ru.descend.exileforge.features.data.user
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import ru.descend.exileforge.base.route.BaseRoute
import ru.descend.exileforge.base.route.respondOk
import ru.descend.exileforge.features.data.auth.AuthSessionRepository
import ru.descend.exileforge.server.addons.CallerKey
import ru.descend.exileforge.server.addons.LOGIN_LIMIT
import ru.descend.exileforge.server.addons.PASSWORD_LIMIT

/**
 * Аккаунт: вход, сессия, пароль. Общего CRUD у аккаунтов нет - клиент его не зовёт.
 *
 * Вход по паролю и по устройству отвечает аккаунтом и токеном; дальше каждый запрос несёт
 * токен в `Authorization: Bearer`. Всё, что несёт секрет, идёт в теле POST: строка запроса
 * оседает в логах прокси и балансировщиков.
 */
class UserRoute(
    private val repo: UserRepository,
    private val service: ru.descend.exileforge.features.logic.auth.UserService,
    private val sessions: AuthSessionRepository,
) : BaseRoute<User>(
    repository = repo,
    entitySerializer = User.serializer(),
    operations = emptySet(),
) {
    override fun additionalRoutes(route: Route) = with(route) {
        rateLimit(LOGIN_LIMIT) {
            post("/login") {
                val request = call.receive<LoginRequest>()
                call.respondOk(signedIn(service.authenticate(request.login, request.password)))
            }
            post("/byDeviceId") {
                val (user, secret) = service.createByDevice()
                call.respondOk(signedIn(user).copy(deviceSecret = secret))
            }
            post("/login/byDeviceId") {
                call.respondOk(signedIn(service.loginByDevice(call.receive<DeviceRequest>().deviceId)))
            }
        }
        // Аккаунт текущей сессии: так клиент восстанавливает вход по сохранённому токену.
        get("/me") {
            call.respondOk(repo.findById(call.attributes[CallerKey].user._id)?.toResponse())
        }
        post("/logout") {
            sessions.revoke(call.attributes[CallerKey].token)
            call.respondOk("system.success")
        }
        // Меняет пароль своего аккаунта и гасит все остальные его сессии: тот, кто знал
        // старый пароль, выходит сразу, а не через тридцать дней.
        rateLimit(PASSWORD_LIMIT) {
            post("/changePassword") {
                val caller = call.attributes[CallerKey]
                val change = call.receive<PasswordChange>()
                val result = service.changePassword(caller.user._id, change.password, change.newPassword)
                sessions.revokeAll(caller.user._id, except = caller.token)
                call.respondOk(result)
            }
        }
    }

    private suspend fun signedIn(user: User) = LoginResponse(user.toResponse(), sessions.issue(user._id))
}

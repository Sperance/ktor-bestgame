package features.data.user

import base.route.BaseRoute
import base.route.respondOk
import features.data.auth.AuthSessionRepository
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import server.addons.CallerKey
import server.addons.LOGIN_LIMIT

/**
 * Аккаунт: вход, сессия, пароль. Общего CRUD у аккаунтов нет - клиент его не зовёт.
 *
 * Вход по паролю и по устройству отвечает аккаунтом и токеном; дальше каждый запрос несёт
 * токен в `Authorization: Bearer`. Всё, что несёт секрет, идёт в теле POST: строка запроса
 * оседает в логах прокси и балансировщиков.
 */
class UserRoute(
    private val repo: UserRepository,
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
                call.respondOk(signedIn(repo.authenticate(request.login, request.password)))
            }
            post("/byDeviceId") {
                call.respondOk(signedIn(repo.createByDevice(call.receive<DeviceRequest>().deviceId)))
            }
            post("/login/byDeviceId") {
                call.respondOk(signedIn(repo.findByDeviceId(call.receive<DeviceRequest>().deviceId)))
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
        post("/changePassword") {
            val caller = call.attributes[CallerKey]
            val change = call.receive<PasswordChange>()
            val result = repo.changePassword(caller.user._id, change.password, change.newPassword)
            sessions.revokeAll(caller.user._id, except = caller.token)
            call.respondOk(result)
        }
    }

    private suspend fun signedIn(user: User) = LoginResponse(user.toResponse(), sessions.issue(user._id))
}

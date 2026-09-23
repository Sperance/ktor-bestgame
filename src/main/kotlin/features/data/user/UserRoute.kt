package features.data.user

import base.route.ApiMongoResponse
import base.route.BaseRoute
import features.data.auth.AuthSessionRepository
import features.logic.auth.Caller
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import server.addons.CallerKey
import server.addons.LOGIN_LIMIT

/**
 * Аккаунт: вход, сессия, пароль.
 *
 * Вход по паролю и по устройству отвечает аккаунтом и токеном; дальше каждый запрос несёт
 * токен в `Authorization: Bearer`. Всё, что несёт секрет, идёт в теле POST: строка запроса
 * оседает в логах прокси и балансировщиков.
 */
class UserRoute(val repo: UserRepository) : BaseRoute<User, UserResponse>(
    repository = repo,
    entitySerializer = User.serializer(),
    responseSerializer = UserResponse.serializer(),
    toResponse = { it.toResponse() }
), KoinComponent {
    private val sessions: AuthSessionRepository by inject()

    override fun additionalRoutes(route: Route) = with(route) {
        rateLimit(LOGIN_LIMIT) {
            post("/login") {
                val request = call.receive<LoginRequest>()
                val user = repo.authenticate(request.login, request.password)
                call.respond(ApiMongoResponse.ok(LoginResponse(user.toResponse(), sessions.issue(user._id))))
            }
            post("/byDeviceId") {
                val user = repo.createByDevice(call.receive<DeviceRequest>().deviceId)
                call.respond(ApiMongoResponse.ok(LoginResponse(user.toResponse(), sessions.issue(user._id))))
            }
            post("/login/byDeviceId") {
                val user = repo.findByDeviceId(call.receive<DeviceRequest>().deviceId)
                call.respond(ApiMongoResponse.ok(LoginResponse(user.toResponse(), sessions.issue(user._id))))
            }
        }
        // Аккаунт текущей сессии: так клиент восстанавливает вход по сохранённому токену.
        get("/me") {
            val caller: Caller = call.attributes[CallerKey]
            call.respond(ApiMongoResponse.ok(repo.findById(caller.user._id)?.toResponse()))
        }
        post("/logout") {
            sessions.revoke(call.attributes[CallerKey].token)
            call.respond(ApiMongoResponse.ok("system.success"))
        }
        // Меняет пароль своего аккаунта и гасит все остальные его сессии: тот, кто знал
        // старый пароль, выходит сразу, а не через тридцать дней.
        post("/changePassword") {
            val caller = call.attributes[CallerKey]
            val change = call.receive<PasswordChange>()
            val result = repo.changePassword(caller.user._id, change.password, change.newPassword)
            sessions.revokeAll(caller.user._id, except = caller.token)
            call.respond(ApiMongoResponse.ok(result))
        }
        route("/search") {
            get("/active") {
                val users = repo.findActive().map { it.toResponse() }
                call.respond(ApiMongoResponse.ok(users))
            }
            get("/name") {
                val name = call.queryParam("name")
                val users = repo.searchByName(name).map { it.toResponse() }
                call.respond(ApiMongoResponse.ok(users))
            }
            get("/email") {
                val email = call.queryParam("email")
                val user = repo.findByEmail(email)?.toResponse()
                call.respond(ApiMongoResponse.ok(user))
            }
        }
    }
}

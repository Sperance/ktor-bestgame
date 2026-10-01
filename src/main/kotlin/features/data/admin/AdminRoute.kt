package features.data.admin

import base.route.RouteRegistrar
import base.route.apiPath
import base.route.queryParam
import base.route.respondOk
import features.data.auth.AuthSessionRepository
import features.data.user.UserRepository
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * Окно аккаунта администратора (1.69.0): тестировщики - список, новый с логином и случайным паролем, новый пароль, отключение.
 * Весь путь `/api/v1/admin` закрыт политикой доступа для всех, кроме администратора.
 */
class AdminRoute(private val users: UserRepository, private val sessions: AuthSessionRepository) : RouteRegistrar {
    override fun register(routing: Routing) {
        routing.route(apiPath("admin")) {
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

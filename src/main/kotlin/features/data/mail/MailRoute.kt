package features.data.mail

import base.route.RouteRegistrar
import base.route.apiPath
import base.route.heroId
import base.route.queryParam
import base.route.respondOk
import features.logic.auth.caller
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/** Почта (1.69.0): ящик своего аккаунта, прочитать, забрать вложение героем аккаунта, удалить. */
class MailRoute(private val repo: MailRepository) : RouteRegistrar {
    override fun register(routing: Routing) {
        routing.route(apiPath("mail")) {
            get("/inbox") { call.respondOk(repo.inbox(me())) }
            post("/read") { call.respondOk(repo.markRead(me(), call.queryParam("id"))) }
            post("/claim") { call.respondOk(repo.claim(me(), call.queryParam("id"), call.heroId)) }
            post("/delete") {
                repo.remove(me(), call.queryParam("id"))
                call.respondOk(true)
            }
        }
    }
}

/** Аккаунт запроса: маршрут почты закрыт политикой для невошедших, так что он есть всегда. */
internal suspend fun me(): String = caller()?.user?._id ?: error("signed-in route without a caller")

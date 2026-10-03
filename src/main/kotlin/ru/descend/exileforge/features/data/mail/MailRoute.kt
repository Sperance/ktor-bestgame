package ru.descend.exileforge.features.data.mail
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import ru.descend.exileforge.base.route.RouteRegistrar
import ru.descend.exileforge.base.route.apiPath
import ru.descend.exileforge.base.route.heroId
import ru.descend.exileforge.base.route.queryParam
import ru.descend.exileforge.base.route.respondOk
import ru.descend.exileforge.features.logic.auth.caller

/** Почта (1.69.0): ящик своего аккаунта, прочитать, забрать вложение героем аккаунта, удалить. */
class MailRoute(private val repo: MailRepository, private val service: ru.descend.exileforge.features.logic.mail.MailService) : RouteRegistrar {
    override fun register(routing: Routing) {
        routing.route(apiPath("mail")) {
            get("/inbox") { call.respondOk(repo.inbox(me())) }
            post("/read") { call.respondOk(repo.markRead(me(), call.queryParam("id"))) }
            post("/claim") { call.respondOk(service.claim(me(), call.queryParam("id"), call.heroId)) }
            post("/delete") {
                repo.remove(me(), call.queryParam("id"))
                call.respondOk(true)
            }
        }
    }
}

/** Аккаунт запроса: маршрут почты закрыт политикой для невошедших, так что он есть всегда. */
internal suspend fun me(): String = caller()?.user?._id ?: error("signed-in route without a caller")

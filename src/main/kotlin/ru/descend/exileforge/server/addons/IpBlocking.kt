package ru.descend.exileforge.server.addons
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.plugins.origin
import io.ktor.server.response.respond
import ru.descend.exileforge.base.exception.model.AuthExceptions
import ru.descend.exileforge.base.route.ApiMongoResponse
import ru.descend.exileforge.features.caches.BlockListCache
import ru.descend.exileforge.features.data.auth.AuthSessionRepository
import ru.descend.exileforge.features.logic.auth.BlockWatch

/** Блокировка по адресу (1.46.0: с учётом срока и ответом 403); по аккаунту - в [configureAccess]. */
fun Application.configureIpBlocking(blockListCache: BlockListCache, sessions: AuthSessionRepository) {
    BlockWatch(blockListCache, sessions).start(this)

    intercept(ApplicationCallPipeline.Monitoring) {
        if (blockListCache.isBlocked(call.request.origin.remoteAddress)) {
            call.respond(HttpStatusCode.Forbidden, ApiMongoResponse.error(AuthExceptions.funExceptionBlocked("block")))
            finish()
        }
    }
}

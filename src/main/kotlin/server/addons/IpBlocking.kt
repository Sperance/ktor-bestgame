package server.addons

import base.exception.model.AuthExceptions
import base.route.ApiMongoResponse
import features.caches.BlockListCache
import features.data.auth.AuthSessionRepository
import features.logic.auth.BlockWatch
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.plugins.origin
import io.ktor.server.response.respond
import org.koin.mp.KoinPlatform.getKoin

/** Блокировка по адресу (1.46.0: с учётом срока и ответом 403); по аккаунту - в [configureAccess]. */
fun Application.configureIpBlocking() {
    val blockListCache: BlockListCache by getKoin().inject()
    val sessions: AuthSessionRepository by getKoin().inject()
    BlockWatch(blockListCache, sessions).start(this)

    intercept(ApplicationCallPipeline.Monitoring) {
        if (blockListCache.isBlocked(call.request.origin.remoteAddress)) {
            call.respond(HttpStatusCode.Forbidden, ApiMongoResponse.error(AuthExceptions.funExceptionBlocked("block")))
            finish()
        }
    }
}

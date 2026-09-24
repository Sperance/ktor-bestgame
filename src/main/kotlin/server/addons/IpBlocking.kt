package server.addons

import base.route.ApiMongoResponse
import features.caches.BlockListCache
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.plugins.origin
import io.ktor.server.response.respond
import org.koin.mp.KoinPlatform.getKoin

fun Application.configureIpBlocking() {

    val blockListCache: BlockListCache by getKoin().inject()

    intercept(ApplicationCallPipeline.Monitoring) {
        val clientIp = call.request.origin.remoteAddress
        if (blockListCache.isBlocked(clientIp)) {
            call.respond(ApiMongoResponse.ok("system.blocked"))
            finish()
        }
    }
}
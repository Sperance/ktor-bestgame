package server.addons

import base.route.ApiMongoResponse
import extensions.printLog
import features.caches.BlockListCache
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.plugins.origin
import io.ktor.server.response.respond

fun Application.configureIpBlocking() {
    intercept(ApplicationCallPipeline.Monitoring) {
        val clientIp = call.request.origin.remoteAddress
        printLog("REQUEDT FROM $clientIp")
        val cacheBlocking = BlockListCache.getCache()
        val blocked = cacheBlocking.find { it.address ==  clientIp }
        if (blocked != null) {
            call.respond(ApiMongoResponse.ok("You are blocked"))
            finish()
        }
    }
}
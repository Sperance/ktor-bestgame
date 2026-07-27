package server.addons

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.ratelimit.RateLimit
import kotlin.time.Duration.Companion.minutes

fun Application.configureRateLimit() {
    install(RateLimit) {
        global {
            rateLimiter(limit = 1000, refillPeriod = 1.minutes)
        }
    }
}
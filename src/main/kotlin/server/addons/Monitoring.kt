package server.addons

import extensions.formatTimestamp
import extensions.printLog
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.origin
import io.ktor.server.request.*
import io.ktor.server.sessions.sessionId
import io.ktor.util.AttributeKey
import org.slf4j.event.*

fun Application.configureMonitoring() {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }

        // Детальный формат с телом запроса (осторожно с большими запросами)
        format { call ->
            val status = call.response.status()?.value ?: 0
            val method = call.request.httpMethod
            val path = call.request.path()
            val sessionId = call.sessionId
            val userAgent = call.request.headers["User-Agent"] ?: "unknown"
            val remoteHost = call.request.origin.remoteHost
            val contentLength = call.request.contentLength() ?: 0
            val startTime = call.attributes[AttributeKey<Long>("CallStartTime")]
            val duration = System.currentTimeMillis() - startTime

            val res = buildString {
                append("[HTTP] $method $path ")
                append("| Status: $status ")
                append("| Session: $sessionId ")
                append("| Remote: $remoteHost ")
                append("| StartTime: ${formatTimestamp(startTime)} ")
                append("| Duration: ${duration}ms ")
                append("| UA: ${userAgent.take(50)} ") // ограничиваем длину
                append("| Size: ${contentLength}B")
            }
            printLog(res)

            res
        }
    }
}
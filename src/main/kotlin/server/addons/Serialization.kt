package server.addons

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json

/**
 * Общий Json-конфиг, используется и в ContentNegotiation, и в BaseRoute.respond()
 */
val AppJson = Json {
    prettyPrint = true
    isLenient = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(AppJson)
    }
}
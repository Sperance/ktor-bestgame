package ru.descend.shared.http

import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import kotlinx.serialization.json.Json
import ru.descend.infrastructure.http.AppJson

val CommandJson = Json(AppJson) { ignoreUnknownKeys = false; isLenient = false; coerceInputValues = false }
suspend inline fun <reified T> ApplicationCall.receiveCommand(): T {
    val text = receiveText()
    if (text.length > 262144) invalid("Request is too large")
    return try { CommandJson.decodeFromString<T>(text) } catch (e: kotlinx.serialization.SerializationException) { invalid("Invalid request fields or values") }
}

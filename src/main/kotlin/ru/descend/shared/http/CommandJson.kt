package ru.descend.shared.http

import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveChannel
import io.ktor.utils.io.readAvailable
import kotlinx.serialization.json.Json
import ru.descend.infrastructure.http.AppJson

val CommandJson = Json(AppJson) { ignoreUnknownKeys = false; isLenient = false; coerceInputValues = false }
suspend inline fun <reified T> ApplicationCall.receiveCommand(): T {
    val text = commandBody()
    return try { CommandJson.decodeFromString<T>(text) } catch (e: kotlinx.serialization.SerializationException) { invalid("Invalid request fields or values") }
}

suspend fun ApplicationCall.commandBody(): String {
    if (request.headers["Content-Type"]?.substringBefore(';')?.trim() != "application/json") invalid("Expected application/json")
    val channel = receiveChannel()
    val buffer = ByteArray(8192)
    val output = java.io.ByteArrayOutputStream()
    while (true) {
        val count = channel.readAvailable(buffer, 0, buffer.size)
        if (count < 0) break
        if (output.size() + count > 262144) invalid("Request is too large")
        output.write(buffer, 0, count)
    }
    return output.toByteArray().decodeToString(throwOnInvalidSequence = true)
}

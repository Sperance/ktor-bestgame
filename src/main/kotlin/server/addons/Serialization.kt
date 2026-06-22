package server.addons

import extensions.ObjectIdSerializer
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import org.bson.types.ObjectId

/**
 * Общий Json-конфиг, используется и в ContentNegotiation, и в BaseRoute.respond()
 */
val AppJson = Json {
    prettyPrint = true
    isLenient = true
    ignoreUnknownKeys = true
    encodeDefaults = true

    serializersModule = SerializersModule {
        contextual(ObjectId::class, ObjectIdSerializer)
    }
}

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(AppJson)
    }
}
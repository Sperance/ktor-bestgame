package ru.descend.infrastructure.http

import com.mongodb.MongoException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.coroutines.CancellationException
import ru.descend.shared.error.BaseException
import ru.descend.shared.http.*

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<ApiFailure> { call, e -> call.respond(e.status, ApiMongoResponse.error(BaseException(e.message, "API", null, e.code))) }
        exception<BaseException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ApiMongoResponse.error(BaseException("Request rejected by validation", "API", null, "VALIDATION_FAILED"))) }
        exception<IllegalArgumentException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ApiMongoResponse.error(BaseException("Invalid request", "API", null, "INVALID_REQUEST"))) }
        exception<Throwable> { call, e ->
            if (e is CancellationException) throw e
            val status = if (e is MongoException && (e.hasErrorLabel("TransientTransactionError") || e.code == 11000)) HttpStatusCode.Conflict else HttpStatusCode.InternalServerError
            // Never return raw exception/driver/serialization messages: they may contain submitted secrets.
            call.respond(status, ApiMongoResponse.error(BaseException(if (status == HttpStatusCode.Conflict) "Concurrent update; reload before retrying" else "Request could not be completed", "API", null, "REQUEST_FAILED")))
        }
    }
}

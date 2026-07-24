package server.addons

import base.exception.BaseException
import base.route.ApiMongoResponse
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.uri
import io.ktor.server.response.respond

fun Application.configureStatusPages() {
    install(StatusPages) {

        // Обработка несуществующих эндпоинтов
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(
                HttpStatusCode.NotFound,
                ApiMongoResponse.error(BaseException("Not find endpoint ${call.request.uri.substringBefore("?")}", "StatusPage", null, "SP_001"))
            )
        }

        // Обработка неразрешённых методов (Method Not Allowed)
        status(HttpStatusCode.MethodNotAllowed) { call, status ->
            call.respond(
                status,
                ApiMongoResponse.error(BaseException("Unsupported method ${call.request.uri.substringBefore("?")}", "StatusPage", null, "SP_002"))
            )
        }

        status(HttpStatusCode.Unauthorized) { call, status ->
            call.respond(
                status,
                ApiMongoResponse.error(BaseException("Unathorized ${call.request.uri.substringBefore("?")}. Please login", "StatusPage", null, "SP_003"))
            )
        }

        status(HttpStatusCode.TooManyRequests) { call, status ->
            val retryAfter = call.response.headers["Retry-After"]
            call.respond(
                status,
                ApiMongoResponse.error(BaseException("Too many rquests, please try again in $retryAfter seconds. ${call.request.uri.substringBefore("?")}", "StatusPage", null, "SP_004"))
            )
        }

        // ── Бизнес-исключения приложения ──
        exception<BaseException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ApiMongoResponse.error(cause))
        }

        exception<JsonConvertException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ApiMongoResponse.error(BaseException(cause.cause?.message?:cause.message, "StatusPage", null, "SP_100")))
        }

        // Общий обработчик (должен быть последним)
        exception<Throwable> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, ApiMongoResponse.error(BaseException(cause.cause?.message?:cause.message, "StatusPage", null, "SP_500")))
        }
    }
}
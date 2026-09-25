package server.addons

import base.exception.BaseException
import base.exception.model.AuthExceptions
import extensions.printLog
import base.route.ApiMongoResponse
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.UnsupportedMediaTypeException
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

        // ── Отказ в доступе: 401 - войдите, 403 - нельзя ──
        exception<AuthExceptions.AuthException> { call, cause ->
            call.respond(HttpStatusCode.fromValue(cause.status), ApiMongoResponse.error(cause))
        }

        // ── Бизнес-исключения приложения ──
        exception<BaseException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ApiMongoResponse.error(cause))
        }

        exception<JsonConvertException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ApiMongoResponse.error(BaseException(cause.cause?.message?:cause.message, "StatusPage", null, "SP_100")))
        }

        // Ktor заворачивает ошибку разбора тела в BadRequestException, так что обработчик
        // JsonConvertException выше её не видит: без этого кривое тело отвечало 500
        exception<BadRequestException> { call, cause ->
            val reason = generateSequence(cause as Throwable) { it.cause }.last().message ?: cause.message
            call.respond(HttpStatusCode.BadRequest, ApiMongoResponse.error(BaseException(reason, "StatusPage", null, "SP_100")))
        }

        exception<UnsupportedMediaTypeException> { call, cause ->
            call.respond(HttpStatusCode.UnsupportedMediaType, ApiMongoResponse.error(BaseException(cause.message, "StatusPage", null, "SP_415")))
        }

        // Общий обработчик (должен быть последним)
        // Подробности непредвиденной ошибки остаются в логе сервера: сообщения драйвера Mongo
        // и стек - не то, что стоит отдавать любому, кто прислал кривой запрос.
        exception<Throwable> { call, cause ->
            printLog("[SP_500] ${call.request.uri.substringBefore("?")}: ${cause::class.simpleName}: ${cause.message}\n${cause.stackTraceToString()}", true)
            call.respond(HttpStatusCode.InternalServerError, ApiMongoResponse.error(BaseException("Internal server error", "StatusPage", null, "SP_500")))
        }
    }
}
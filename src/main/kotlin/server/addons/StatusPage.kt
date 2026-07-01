package server.addons

import base.exception.AppException
import base.exception.ExceptionForCode
import base.route.ApiMongoResponse
import io.ktor.http.HttpStatusCode
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
                ApiMongoResponse.error(
                    message = "Запрашиваемый ресурс не найден: ${call.request.uri.substringBefore("?")}",
                    code = HttpStatusCode.NotFound.value
                )
            )
        }

        // Обработка неразрешённых методов (Method Not Allowed)
        status(HttpStatusCode.MethodNotAllowed) { call, status ->
            call.respond(
                HttpStatusCode.MethodNotAllowed,
                ApiMongoResponse.error(
                    message = "Метод не поддерживается для ${call.request.uri.substringBefore("?")}",
                    code = HttpStatusCode.MethodNotAllowed.value
                )
            )
        }

        exception<ExceptionForCode> { call, cause ->
            val status = HttpStatusCode.fromValue(cause.httpCode)
            call.respond(status, ApiMongoResponse.error(cause.message, code = cause.errorCode))
        }

        // ── Бизнес-исключения приложения ──
        exception<AppException> { call, cause ->
            val status = HttpStatusCode.fromValue(cause.httpCode)
            call.respond(status, ApiMongoResponse.error(cause.message, code = cause.httpCode))
        }

        // Обработка IllegalArgumentException
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                status = HttpStatusCode.PreconditionFailed,
                message = ApiMongoResponse.error(
                    message = cause.message ?: "Некорректный запрос",
                    code = HttpStatusCode.PreconditionFailed.value
                )
            )
        }

        // Обработка IllegalStateException
        exception<IllegalStateException> { call, cause ->
            call.respond(
                status = HttpStatusCode.PreconditionFailed,
                message = ApiMongoResponse.error(
                    message = cause.message ?: "Некорректный запрос",
                    code = HttpStatusCode.PreconditionFailed.value
                )
            )
        }

        // Общий обработчик (должен быть последним)
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled exception", cause)
            call.respond(
                status = HttpStatusCode.InternalServerError,
                message = ApiMongoResponse.error(
                    message = "Внутренняя ошибка сервера",
                    code = 500
                )
            )
        }
    }
}
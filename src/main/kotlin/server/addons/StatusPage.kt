package server.addons

import base.exception.AppException
import base.exception.BadRequestException
import base.exception.ExceptionForCode
import base.exception.NotFoundException
import base.model.ApiResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import java.sql.SQLException

fun Application.configureStatusPages() {
    install(StatusPages) {

        // Обработка несуществующих эндпоинтов
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(
                HttpStatusCode.NotFound,
                ApiResponse.error(
                    message = "Запрашиваемый ресурс не найден: ${call.request.uri.substringBefore("?")}",
                    code = HttpStatusCode.NotFound.value
                )
            )
        }

        // Обработка неразрешённых методов (Method Not Allowed)
        status(HttpStatusCode.MethodNotAllowed) { call, status ->
            call.respond(
                HttpStatusCode.MethodNotAllowed,
                ApiResponse.error(
                    message = "Метод не поддерживается для ${call.request.uri.substringBefore("?")}",
                    code = HttpStatusCode.MethodNotAllowed.value
                )
            )
        }

        exception<ExceptionForCode> { call, cause ->
            val status = HttpStatusCode.fromValue(cause.httpCode)
            call.respond(status, ApiResponse.error(cause.message, code = cause.errorCode))
        }

        // ── Бизнес-исключения приложения ──
        exception<AppException> { call, cause ->
            val status = HttpStatusCode.fromValue(cause.httpCode)
            call.respond(status, ApiResponse.error(cause.message, code = cause.httpCode))
        }

        // Обработка NotFoundException (из вашего base.exception)
        exception<NotFoundException> { call, cause ->
            call.respond(
                status = HttpStatusCode.NotFound,
                message = ApiResponse.error(
                    message = cause.message,
                    code = HttpStatusCode.NotFound.value
                ),
            )
        }

        // Обработка BadRequestException
        exception<BadRequestException> { call, cause ->
            call.respond(
                status = HttpStatusCode.BadRequest,
                message = ApiResponse.error(
                    message = cause.message,
                    code = HttpStatusCode.BadRequest.value
                )
            )
        }

        // Обработка IllegalArgumentException
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                status = HttpStatusCode.PreconditionFailed,
                message = ApiResponse.error(
                    message = cause.message ?: "Некорректный запрос",
                    code = HttpStatusCode.PreconditionFailed.value
                )
            )
        }

        // Обработка IllegalStateException
        exception<IllegalStateException> { call, cause ->
            call.respond(
                status = HttpStatusCode.PreconditionFailed,
                message = ApiResponse.error(
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
                message = ApiResponse.error(
                    message = "Внутренняя ошибка сервера",
                    code = 500
                )
            )
        }
    }
}
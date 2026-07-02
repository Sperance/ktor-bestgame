package server.addons

import base.exception.AppException
import base.exception.BaseException
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
                ApiMongoResponse.error(BaseException("Not find endpoint ${call.request.uri.substringBefore("?")}", "StatusPage", null, "SP_001"))
            )
        }

        // Обработка неразрешённых методов (Method Not Allowed)
        status(HttpStatusCode.MethodNotAllowed) { call, status ->
            call.respond(
                HttpStatusCode.MethodNotAllowed,
                ApiMongoResponse.error(BaseException("Unsupported method ${call.request.uri.substringBefore("?")}", "StatusPage", null, "SP_002"))
            )
        }

        // ── Бизнес-исключения приложения ──
        exception<BaseException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ApiMongoResponse.error(cause))
        }

        // ── Бизнес-исключения приложения ──
        exception<AppException> { call, cause ->
            call.respond(HttpStatusCode.Forbidden, ApiMongoResponse.error(cause.httpCode, cause.message))
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
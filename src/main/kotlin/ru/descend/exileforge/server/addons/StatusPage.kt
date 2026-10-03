package ru.descend.exileforge.server.addons
import com.sperance.exileforge.rules.RuleViolation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import ru.descend.exileforge.base.exception.BaseException
import ru.descend.exileforge.base.exception.BaseRepositoryExceptions
import ru.descend.exileforge.base.exception.model.AuthExceptions
import ru.descend.exileforge.base.exception.model.CampaignExceptions
import ru.descend.exileforge.base.exception.model.IdempotencyExceptions
import ru.descend.exileforge.base.route.ApiMongoResponse
import ru.descend.exileforge.extensions.printLog

fun Application.configureStatusPages() {
    install(StatusPages) {
        // Обработка несуществующих эндпоинтов
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(
                HttpStatusCode.NotFound,
                ApiMongoResponse.error(BaseException("Not find endpoint ${call.request.uri.substringBefore("?")}", "StatusPage", null, "SP_001")),
            )
        }

        // Обработка неразрешённых методов (Method Not Allowed)
        status(HttpStatusCode.MethodNotAllowed) { call, status ->
            call.respond(
                status,
                ApiMongoResponse.error(BaseException("Unsupported method ${call.request.uri.substringBefore("?")}", "StatusPage", null, "SP_002")),
            )
        }

        status(HttpStatusCode.Unauthorized) { call, status ->
            call.respond(
                status,
                ApiMongoResponse.error(BaseException("Unathorized ${call.request.uri.substringBefore("?")}. Please login", "StatusPage", null, "SP_003")),
            )
        }

        status(HttpStatusCode.TooManyRequests) { call, status ->
            val retryAfter = call.response.headers["Retry-After"]
            call.respond(
                status,
                ApiMongoResponse.error(BaseException("Too many rquests, please try again in $retryAfter seconds. ${call.request.uri.substringBefore("?")}", "StatusPage", null, "SP_004")),
            )
        }

        // ── Отказы приложения и правил игры: код и тело - см. [refusal] ──
        exception<BaseException> { call, cause -> call.respondRefusal(cause) }
        exception<RuleViolation> { call, cause -> call.respondRefusal(cause) }

        // Разбор тела (1.53.0): подробности - в лог, клиенту одно слово: текст kotlinx называет классы сервера
        exception<JsonConvertException> { call, cause ->
            printLog("[SP_100] ${call.request.uri.substringBefore("?")}: ${cause.cause?.message ?: cause.message}", true)
            call.respond(HttpStatusCode.BadRequest, ApiMongoResponse.error(BaseException("Malformed request body", "StatusPage", null, "SP_100")))
        }

        // Ktor заворачивает ошибку разбора тела в BadRequestException, так что обработчик
        // JsonConvertException выше её не видит: без этого кривое тело отвечало 500
        exception<BadRequestException> { call, cause ->
            val reason = generateSequence(cause as Throwable) { it.cause }.last().message ?: cause.message
            printLog("[SP_100] ${call.request.uri.substringBefore("?")}: $reason", true)
            call.respond(HttpStatusCode.BadRequest, ApiMongoResponse.error(BaseException("Malformed request body", "StatusPage", null, "SP_100")))
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

/**
 * Бизнес-отказ как ответ: 401/403 доступа и 409/400/422 повтора команды - своим кодом, прочие отказы
 * приложения и правил игры - 400. `null` - не отказ, а сбой: его отвечает общий обработчик.
 */
fun refusal(cause: Throwable): Pair<HttpStatusCode, BaseException>? = when (cause) {
    is AuthExceptions.AuthException -> HttpStatusCode.fromValue(cause.status) to cause

    is IdempotencyExceptions.IdempotencyException -> HttpStatusCode.fromValue(cause.status) to cause

    is CampaignExceptions.PayloadException -> HttpStatusCode.UnprocessableEntity to cause

    is BaseRepositoryExceptions.BaseRepositoryException -> (if (cause.errorCode == BaseRepositoryExceptions.UNAVAILABLE) HttpStatusCode.ServiceUnavailable else HttpStatusCode.BadRequest) to cause

    is BaseException -> HttpStatusCode.BadRequest to cause

    // Отказ правил игры (1.0.0): код и аргументы шаблона словаря, как у отказов сервера
    is RuleViolation -> HttpStatusCode.BadRequest to BaseException(cause.message, "Rules", null, cause.code, cause.args)

    else -> null
}

/** Отвечает бизнес-отказом [cause]; `false` - это не отказ, ответа не было. */
suspend fun ApplicationCall.respondRefusal(cause: Throwable): Boolean {
    val (status, error) = refusal(cause) ?: return false
    respond(status, ApiMongoResponse.error(error))
    return true
}

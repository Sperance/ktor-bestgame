package base.route

import base.exception.BaseRouteExceptions
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import kotlinx.serialization.KSerializer
import server.addons.AppJson

/** Обязательный параметр строки запроса; нет его - ошибка запроса, а не пустая строка. */
fun ApplicationCall.queryParam(name: String): String =
    request.queryParameters[name] ?: throw BaseRouteExceptions.funExceptionQuery("queryParam", name)

/** Необязательный параметр строки запроса: пустой считается отсутствующим. */
fun ApplicationCall.optionalParam(name: String): String? = request.queryParameters[name]?.takeIf { it.isNotBlank() }

/** Параметр с значением по умолчанию, если его нет или он не разбирается в [E]. */
inline fun <reified E> ApplicationCall.queryParam(name: String, default: E): E {
    val value = request.queryParameters[name]
    return if (value == null) default else when (E::class) {
        String::class -> value as E
        Int::class -> value.toIntOrNull() as? E ?: default
        Long::class -> value.toLongOrNull() as? E ?: default
        Double::class -> value.toDoubleOrNull() as? E ?: default
        Boolean::class -> value.toBooleanStrictOrNull() as? E ?: default
        else -> default
    }
}

/** Персонаж, от имени которого идёт команда: его принадлежность уже проверил доступ. */
val ApplicationCall.characterId: String get() = queryParam("characterId")

/** Карта кампании команды. */
val ApplicationCall.mapCode: String get() = queryParam("mapCode")

/** Экземпляр экипировки из инвентаря, над которым идёт команда. */
val ApplicationCall.inventoryId: String get() = queryParam("inventoryId")

/** `?id=` документа коллекции, проверенный на формат ObjectId. */
fun ApplicationCall.idParam(): String = requireId(request.queryParameters["id"] ?: "<NULL>", "idParam")

fun requireId(id: String, method: String): String =
    id.takeIf { it.length == 24 } ?: throw BaseRouteExceptions.funExceptionFormatId(method, id)

/** Успешный ответ в общем конверте. */
suspend inline fun <reified T> ApplicationCall.respondOk(data: T) = respond(ApiMongoResponse.ok(data))

/** Ответ готовым сериализатором - для обобщённых маршрутов, где тип стёрт. */
suspend fun <T> ApplicationCall.respondJson(serializer: KSerializer<T>, value: T) =
    respondText(AppJson.encodeToString(serializer, value), ContentType.Application.Json, HttpStatusCode.OK)

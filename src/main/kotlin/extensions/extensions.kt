package extensions

import config.LogManager
import io.ktor.server.routing.Route
import io.ktor.server.routing.path
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.bson.types.ObjectId
import java.security.MessageDigest
import kotlin.time.Clock
import kotlin.time.Instant

fun printLog(text: Any? = "", system: Boolean = false) {
    if (text is String && system)
        LogManager.log(text)
    else
        LogManager.log("\t$text")
}

fun LocalDateTime.Companion.now() = Clock.System.now().toLocalDateTime(TimeZone.UTC)

/**
 * Детерминированный ObjectId, выведенный из строки.
 *
 * Нужен сидерам: пересев справочника не должен ломать ссылки на его записи.
 */
fun String.toStableObjectId(): String =
    ObjectId(MessageDigest.getInstance("MD5").digest(toByteArray()).copyOf(12)).toHexString()

fun Double.to1Digits() = String.format("%.1f", this).replace(",", ".").toDouble()

fun formatTimestamp(timestamp: Long): String {
    // Преобразуем Long в Instant
    val instant = Instant.fromEpochMilliseconds(timestamp)

    // Конвертируем в LocalDateTime с указанием часового пояса
    val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())

    // Форматируем в строку
    return dateTime.toString() // "2026-07-24T10:15:32.123"
}

val ALL_ROUTES = mutableSetOf<RouteInfo>()
fun Route.saveChildren(depth: Int = 0) {
    fun traverse(route: Route, currentDepth: Int) {
        if (route.children.count() == 0) {
            val method = route.selector?.toString()?.replace("method:", "") ?: "UNKNOWN"

            if (route.path.contains("/swagger/")) return

            ALL_ROUTES.add(
                RouteInfo(
                    path = route.path,
                    method = method
                )
            )
        } else {
            route.children.forEach { child ->
                traverse(child, currentDepth + 1)
            }
        }
    }

    traverse(this, depth)
}
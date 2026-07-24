package extensions

import config.LogManager
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

fun printLog(text: Any?) {
    if (text is String && (text.contains("TR::") || text.contains("*****") || text.contains("[HTTP]")))
        LogManager.log(text)
    else
        LogManager.log("\t$text")
}

fun LocalDateTime.Companion.now() = Clock.System.now().toLocalDateTime(TimeZone.UTC)

fun Double.getPercent(value: Double) : Double {
    return ((this / 100.0) * value).to1Digits()
}

fun Double.addPercent(value: Double) : Double {
    return (this + getPercent(value)).to1Digits()
}

fun Double.removePercent(value: Double) : Double {
    return (this - getPercent(value)).to1Digits()
}

fun Double.format(digits: Int) = "%.${digits}f".format(this)

fun Double.to1Digits() = String.format("%.1f", this).replace(",", ".").toDouble()

fun Any.haveField(name: String) = this::class.java.declaredFields.find { it.isAccessible = true ; it.name == name } != null
fun Any.getField(name: String) = this::class.java.declaredFields.find { it.isAccessible = true ; it.name == name }?.get(this)
fun Any.putField(name: String, value: Any?) = this::class.java.declaredFields.find { it.isAccessible = true ; it.name == name }?.set(this, value)

fun String?.toIntPossible() : Boolean {
    if (this == null) return false
    return this.toIntOrNull() != null
}

fun Any?.isAllNullOrEmpty() : Boolean {
    if (this == null) return true
    when (this) {
        is String -> { return this.isEmpty() }
        is Number -> { return this.isNullOrZero() }
    }
    return false
}

fun Number?.isNullOrZero() : Boolean {
    if (this == null) return true
    if (this == 0) return true
    return false
}

fun formatTimestamp(timestamp: Long): String {
    // Преобразуем Long в Instant
    val instant = Instant.fromEpochMilliseconds(timestamp)

    // Конвертируем в LocalDateTime с указанием часового пояса
    val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())

    // Форматируем в строку
    return dateTime.toString() // "2026-07-24T10:15:32.123"
}
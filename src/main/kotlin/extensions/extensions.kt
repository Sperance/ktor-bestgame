package extensions

import application.DatabaseConfig.dbQuery
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.util.toZonedDateTime
import io.ktor.utils.io.InternalAPI
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import server.enums.EnumSQLTypes
import java.util.Date
import kotlin.toString

fun printLog(text: Any?) {
    println("[PRINTLOG] $text")
}

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

fun generateMapError(call: ApplicationCall, errorPair: Pair<Int, String>): MutableMap<String, String> {
    val map = mutableMapOf<String, String>()
    map["errorCode"] = errorPair.first.toString()
    map["errorDescription"] = errorPair.second
    map["errorType"] = call.request.httpMethod.value
    map["errorUri"] = call.request.uri
    map["requestKey"] = call.response.headers["ERA-key"].toString()
    return map
}

private suspend fun executeScript(script: String): String? {
    printLog("[executeScript] $script")
    try {
        dbQuery {
            exec(script.trimIndent())
        }
        return null
    } catch (e: Exception) {
        printLog("[executeScript] Error: ${e.localizedMessage}")
        return e.localizedMessage
    }
}

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

@OptIn(InternalAPI::class)
fun LocalDateTime.Companion.currectDatetime(): LocalDateTime = Date().toZonedDateTime().toLocalDateTime().toKotlinLocalDateTime()

suspend fun ApplicationCall.respond(response: ResultResponse) {
    try {
        when(response) {
            is ResultResponse.Error -> {
                this.response.headers.append("Answer-TimeStamp", LocalDateTime.currectDatetime().toString())
                this.response.headers.append("Answer-Error", response.message.toString())
                respond(
                    status = HttpStatusCode(350, "Request error"),
                    message = response.message)
            }
            is ResultResponse.Success -> {
                this.response.headers.append("Answer-TimeStamp", LocalDateTime.currectDatetime().toString())
                response.headers?.forEach { (key, value) ->
                    this.response.headers.append(key, value.toString())
                }
                respond(status = HttpStatusCode.OK, message = response.data ?: "")
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        printLog("[ApplicationCall] Error: ${e.localizedMessage}")
    }
}
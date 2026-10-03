package features.data.bugReport

import base.exception.BaseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Куда уходит отобранный администратором отчёт (1.70.0): ссылка на созданную задачу. */
fun interface FeedbackExport {
    suspend fun export(report: BugReport, login: String?): String
}

/**
 * Выгрузка в Asana (1.70.0): отчёт становится задачей проекта - ошибка в секции [bugSection], предложение в
 * [suggestionSection]. Имя - начало текста игрока, описание - весь текст, экран, контекст, хвост журнала и id отчёта.
 * Без [token] выгрузка выключена: отказ `BUG_005`, а не молчаливый пропуск.
 */
class AsanaExport(private val token: String?, private val project: String, private val bugSection: String, private val suggestionSection: String) : FeedbackExport {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(TIMEOUT_S)).build()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun export(report: BugReport, login: String?): String {
        val token = token ?: throw BaseException("Asana export is off: no ASANA_TOKEN", "BugReport", "asana", "BUG_005")
        val task = post(
            token,
            "tasks?opt_fields=permalink_url",
            buildJsonObject {
                putJsonObject("data") {
                    put("name", title(report))
                    put("notes", notes(report, login))
                    putJsonArray("projects") { add(JsonPrimitive(project)) }
                }
            },
        )
        val gid = task["gid"]?.jsonPrimitive?.content
        // Секцию API ставит только отдельным вызовом, не при создании задачи.
        if (gid != null) {
            post(
                token,
                "sections/${if (report.kind == FeedbackKind.BUG) bugSection else suggestionSection}/addTask",
                buildJsonObject { putJsonObject("data") { put("task", gid) } },
            )
        }
        return task["permalink_url"]?.jsonPrimitive?.content.orEmpty()
    }

    /** Один вызов API; всё, кроме 2xx, - отказ `BUG_006` со словами самой Asana. */
    private suspend fun post(token: String, path: String, body: JsonObject): JsonObject {
        val request = HttpRequest.newBuilder(URI.create("$API/$path"))
            .timeout(Duration.ofSeconds(TIMEOUT_S))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build()
        val response = withContext(Dispatchers.IO) { http.send(request, HttpResponse.BodyHandlers.ofString()) }
        if (response.statusCode() !in 200..299) {
            throw BaseException(
                "Asana refused $path: ${response.statusCode()} ${response.body().take(ERROR_TAIL)}",
                "BugReport",
                "asana",
                "BUG_006",
                listOf(response.statusCode().toString()),
            )
        }
        return json.parseToJsonElement(response.body()).jsonObject["data"]?.jsonObject ?: JsonObject(emptyMap())
    }

    private fun title(report: BugReport): String {
        val line = report.text.lineSequence().first().trim()
        val prefix = if (report.kind == FeedbackKind.BUG) "[Баг] " else "[Предложение] "
        return prefix + if (line.length > TITLE) line.take(TITLE - 1) + "…" else line
    }

    private fun notes(report: BugReport, login: String?): String = buildString {
        appendLine(report.text)
        appendLine()
        appendLine("Отчёт: ${report._id} · ${report.createdAt}")
        appendLine("Автор: ${login ?: report.userId ?: "—"} · ${report.address}")
        if (report.kind == FeedbackKind.SUGGESTION) appendLine("Голоса: +${report.likes.size} / -${report.dislikes.size}")
        if (report.screen.isNotBlank()) appendLine("Экран: ${report.screen}")
        if (report.context.isNotEmpty()) {
            appendLine()
            appendLine("Контекст:")
            report.context.forEach { (k, v) -> appendLine("  $k: $v") }
        }
        if (report.requests.isNotEmpty()) {
            appendLine()
            appendLine("Журнал запросов:")
            report.requests.forEach { appendLine("  $it") }
        }
    }

    private companion object {
        const val API = "https://app.asana.com/api/1.0"
        const val TIMEOUT_S = 15L
        const val TITLE = 120
        const val ERROR_TAIL = 300
    }
}

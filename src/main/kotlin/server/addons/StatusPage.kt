package server.addons

import base.exception.AppException
import base.model.ApiResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import java.sql.SQLException

fun Application.configureStatusPages() {
    install(StatusPages) {

        // ── Бизнес-исключения приложения ──
        exception<AppException> { call, cause ->
            val status = HttpStatusCode.fromValue(cause.httpCode)
            call.respond(status, ApiResponse.error(cause.message))
        }

        // ── SQL-исключения от Exposed / PostgreSQL ──
        exception<ExposedSQLException> { call, cause ->
            val sqlEx = cause.cause as? SQLException
            val sqlState = sqlEx?.sqlState
            val pgMessage = sqlEx?.message.orEmpty()

            val (status, message) = parseSqlException(sqlState, pgMessage)

            if (status == HttpStatusCode.InternalServerError) {
                call.application.environment.log.error(
                    "Database error (sqlState=$sqlState): $pgMessage", cause
                )
            }

            call.respond(status, ApiResponse.error(message))
        }

        // ── Прочие исключения ──
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ApiResponse.error(cause.message ?: "Validation error"))
        }
        exception<IllegalStateException> { call, cause ->
            call.respond(HttpStatusCode.InternalServerError, ApiResponse.error(cause.message ?: "State error"))
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled exception", cause)
            call.respond(HttpStatusCode.InternalServerError, ApiResponse.error("Internal server error"))
        }
    }
}

// ════════════════════════════════════════════════════════════════
//  PostgreSQL sqlState → HTTP-ответ
// ════════════════════════════════════════════════════════════════

private fun parseSqlException(
    sqlState: String?,
    pgMessage: String
): Pair<HttpStatusCode, String> = when (sqlState) {

    // ── Class 23 — Integrity Constraint Violation ───────────

    /**
     * 23505 — unique_violation
     * Попытка вставить / обновить значение, которое нарушает UNIQUE-индекс.
     *
     * PostgreSQL detail:  Key (email)=(john@mail.com) already exists.
     * Ответ клиенту:      "Field 'email' with value 'john@mail.com' already exists"
     */
    "23505" -> {
        val parsed = parseKeyValue(pgMessage)
        val msg = if (parsed != null)
            "Field '${parsed.first}' with value '${parsed.second}' already exists"
        else
            "Duplicate value: $pgMessage"
        HttpStatusCode.Conflict to msg
    }

    /**
     * 23503 — foreign_key_violation
     * INSERT / UPDATE ссылается на несуществующую запись в другой таблице,
     * или DELETE удаляет строку, на которую кто-то ссылается.
     *
     * PostgreSQL detail:  Key (user_id)=(999) is not present in table "users".
     *               или:  Key (id)=(1) is still referenced from table "orders".
     */
    "23503" -> {
        val msg = when {
            pgMessage.contains("is not present in table") -> {
                val parsed = parseKeyValue(pgMessage)
                val table = parseReferencedTable(pgMessage, "is not present in table")
                if (parsed != null && table != null)
                    "Referenced ${table}(${parsed.first}=${parsed.second}) does not exist"
                else
                    "Referenced record does not exist: $pgMessage"
            }
            pgMessage.contains("is still referenced from table") -> {
                val table = parseReferencedTable(pgMessage, "is still referenced from table")
                if (table != null)
                    "Cannot delete: record is still referenced from '$table'"
                else
                    "Cannot delete: record is still referenced by other records"
            }
            else -> "Foreign key violation: $pgMessage"
        }
        HttpStatusCode.Conflict to msg
    }

    /**
     * 23502 — not_null_violation
     * INSERT / UPDATE пытается записать NULL в NOT NULL колонку.
     *
     * PostgreSQL:  null value in column "name" violates not-null constraint
     */
    "23502" -> {
        val column = Regex("""column "(\w+)"""").find(pgMessage)?.groupValues?.get(1)
        val msg = if (column != null)
            "Field '$column' must not be null"
        else
            "Required field is missing (null)"
        HttpStatusCode.BadRequest to msg
    }

    /**
     * 23514 — check_violation
     * Значение не проходит CHECK-ограничение на таблице.
     *
     * PostgreSQL:  new row for relation "users" violates check constraint "users_age_check"
     */
    "23514" -> {
        val constraint = Regex("""constraint "(\w+)"""").find(pgMessage)?.groupValues?.get(1)
        val msg = if (constraint != null)
            "Value violates constraint '$constraint'"
        else
            "Value violates a check constraint"
        HttpStatusCode.BadRequest to msg
    }

    /**
     * 23P01 — exclusion_violation
     * Нарушено EXCLUDE-ограничение (например, пересечение диапазонов дат).
     */
    "23P01" -> {
        val constraint = Regex("""constraint "(\w+)"""").find(pgMessage)?.groupValues?.get(1)
        val msg = if (constraint != null)
            "Value conflicts with existing data (exclusion constraint '$constraint')"
        else
            "Value conflicts with existing data"
        HttpStatusCode.Conflict to msg
    }

    // ── Class 22 — Data Exception ───────────────────────────

    /**
     * 22001 — string_data_right_truncation
     * Строка длиннее, чем varchar(N).
     *
     * PostgreSQL:  value too long for type character varying(20)
     */
    "22001" -> {
        val limit = Regex("""varying\((\d+)\)""").find(pgMessage)?.groupValues?.get(1)
        val msg = if (limit != null)
            "Value is too long (maximum $limit characters)"
        else
            "Value is too long for the field"
        HttpStatusCode.BadRequest to msg
    }

    /**
     * 22003 — numeric_value_out_of_range
     * Число вне допустимого диапазона типа (int, smallint, bigint и т.д.).
     */
    "22003" -> HttpStatusCode.BadRequest to "Numeric value out of allowed range"

    /**
     * 22P02 — invalid_text_representation
     * Невозможно привести строку к требуемому типу (например, 'abc' → integer).
     */
    "22P02" -> {
        val msg = if (pgMessage.contains("invalid input syntax"))
            "Invalid value format: ${extractQuoted(pgMessage) ?: pgMessage}"
        else
            "Invalid data format"
        HttpStatusCode.BadRequest to msg
    }

    /**
     * 22007 — invalid_datetime_format
     * Невалидный формат даты/времени.
     */
    "22007" -> HttpStatusCode.BadRequest to "Invalid date/time format"

    /**
     * 22008 — datetime_field_overflow
     * Дата/время вне допустимого диапазона.
     */
    "22008" -> HttpStatusCode.BadRequest to "Date/time value out of range"

    /**
     * 22012 — division_by_zero
     */
    "22012" -> HttpStatusCode.BadRequest to "Division by zero"

    // ── Class 42 — Syntax / Access ──────────────────────────

    /**
     * 42501 — insufficient_privilege
     * У пользователя БД нет прав на операцию.
     */
    "42501" -> {
        HttpStatusCode.Forbidden to "Insufficient database privileges for this operation"
    }

    /**
     * 42P01 — undefined_table
     * Таблица не существует.
     */
    "42P01" -> {
        HttpStatusCode.InternalServerError to "Database configuration error: table not found"
    }

    /**
     * 42703 — undefined_column
     * Колонка не существует.
     */
    "42703" -> {
        HttpStatusCode.InternalServerError to "Database configuration error: column not found"
    }

    // ── Class 40 — Transaction Rollback ─────────────────────

    /**
     * 40001 — serialization_failure
     * Конфликт при SERIALIZABLE-изоляции, транзакцию нужно повторить.
     */
    "40001" -> HttpStatusCode.Conflict to "Transaction conflict, please retry"

    /**
     * 40P01 — deadlock_detected
     * Обнаружен deadlock, одна из транзакций откатана.
     */
    "40P01" -> HttpStatusCode.Conflict to "Deadlock detected, please retry"

    // ── Class 53 — Insufficient Resources ───────────────────

    /**
     * 53000 — insufficient_resources
     * 53100 — disk_full
     * 53200 — out_of_memory
     * 53300 — too_many_connections
     */
    "53000", "53100", "53200", "53300" -> {
        HttpStatusCode.ServiceUnavailable to "Service temporarily unavailable, please retry later"
    }

    // ── Class 08 — Connection Exception ─────────────────────

    /**
     * 08000, 08001, 08003, 08006 — проблемы с подключением к БД.
     */
    "08000", "08001", "08003", "08006" -> {
        HttpStatusCode.ServiceUnavailable to "Database connection error, please retry later"
    }

    // ── Class 57 — Operator Intervention ────────────────────

    /**
     * 57014 — query_canceled (по таймауту или вручную)
     * 57P01 — admin_shutdown
     * 57P03 — cannot_connect_now
     */
    "57014" -> HttpStatusCode.GatewayTimeout to "Query timed out, please try a simpler request"
    "57P01", "57P03" -> HttpStatusCode.ServiceUnavailable to "Database is restarting, please retry later"

    // ── Всё остальное ───────────────────────────────────────

    else -> HttpStatusCode.InternalServerError to "Database error"
}

// ════════════════════════════════════════════════════════════════
//  Вспомогательные парсеры PostgreSQL-сообщений
// ════════════════════════════════════════════════════════════════

/**
 * Извлекает пару (field, value) из PostgreSQL detail:
 *   Key (email)=(john@mail.com) already exists.
 *   Key (user_id)=(999) is not present in table "users".
 */
private fun parseKeyValue(pgMessage: String): Pair<String, String>? {
    val match = Regex("""\((\w+)\)=\((.+?)\)""").find(pgMessage)
    return match?.let { it.groupValues[1] to it.groupValues[2] }
}

/**
 * Извлекает имя таблицы из фразы вроде:
 *   ... is not present in table "users"
 *   ... is still referenced from table "orders"
 */
private fun parseReferencedTable(pgMessage: String, keyword: String): String? {
    val idx = pgMessage.indexOf(keyword)
    if (idx < 0) return null
    val after = pgMessage.substring(idx + keyword.length)
    return Regex(""""(\w+)"""").find(after)?.groupValues?.get(1)
}

/**
 * Извлекает первую строку в кавычках:
 *   invalid input syntax for type integer: "abc"  →  "abc"
 */
private fun extractQuoted(pgMessage: String): String? {
    return Regex(""""(.+?)"""").find(pgMessage)?.groupValues?.get(1)
}
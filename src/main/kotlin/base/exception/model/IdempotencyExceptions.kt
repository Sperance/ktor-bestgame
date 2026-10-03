package base.exception.model

import base.exception.BaseException

/**
 * Отказы повтора команды по `Idempotency-Key` (1.28.0). Код ответа у них свой, см. [status]:
 * 409 - та же команда ещё выполняется, 400 - ключ негоден, 422 - ключ уже отдан другому запросу.
 */
object IdempotencyExceptions {
    open class IdempotencyException(message: String?, errorMethod: String?, errorCode: String, messageArgs: List<String> = emptyList(), val status: Int) : BaseException(message, "Idempotency", errorMethod, errorCode, messageArgs) {
        override fun toString(): String = "{IdempotencyException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
    }

    fun funExceptionBusy(errorMethod: String, value: String? = "") = IdempotencyException("The same command is still being processed", errorMethod, "IDEM_001", listOf(value.orEmpty()), 409)

    fun funExceptionBadKey(errorMethod: String, value: String? = "") = IdempotencyException("Invalid Idempotency-Key: $value", errorMethod, "IDEM_002", listOf(value.orEmpty()), 400)

    fun funExceptionMismatch(errorMethod: String, value: String? = "") = IdempotencyException("Idempotency-Key $value was already used for another request", errorMethod, "IDEM_003", listOf(value.orEmpty()), 422)
}

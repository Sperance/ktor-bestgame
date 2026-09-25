package base.exception.model

import base.exception.BaseException

object PoolExceptions {
    open class PoolException(message: String?, errorMethod: String?, errorCode: String, messageArgs: List<String> = emptyList()) : BaseException(message, "Pool", errorMethod, errorCode, messageArgs) {
        override fun toString(): String {
            return "{PoolException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funException(errorMethod: String, value: String? = "") = PoolException("Broken pool: $value", errorMethod, "PL_001", listOf(value.orEmpty()))
}

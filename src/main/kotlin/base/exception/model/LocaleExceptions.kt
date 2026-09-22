package base.exception.model

import base.exception.BaseException

object LocaleExceptions {
    open class LocaleException(message: String?, errorMethod: String?, errorCode: String, messageArgs: List<String> = emptyList()) : BaseException(message, "Locale", errorMethod, errorCode, messageArgs) {
        override fun toString(): String {
            return "{LocaleException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funException(errorMethod: String, value: String? = "") = LocaleException(value, errorMethod, "LC_001", listOf(value.orEmpty()))
    fun funExceptionUnknownLanguage(errorMethod: String, value: String? = "") = LocaleException("Language $value is not in the locale manifest", errorMethod, "LC_002", listOf(value.orEmpty()))
    fun funExceptionFileNotFound(errorMethod: String, value: String? = "") = LocaleException("Locale file $value not found in resources", errorMethod, "LC_003", listOf(value.orEmpty()))
}

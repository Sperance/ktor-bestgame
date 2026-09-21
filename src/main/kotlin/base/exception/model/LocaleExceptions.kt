package base.exception.model

import base.exception.BaseException

object LocaleExceptions {
    open class LocaleException(message: String?, errorMethod: String?, errorCode: String) : BaseException(message, "Locale", errorMethod, errorCode) {
        override fun toString(): String {
            return "{LocaleException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funException(errorMethod: String, value: String? = "") = LocaleException(value, errorMethod, "LC_001")
    fun funExceptionUnknownLanguage(errorMethod: String, value: String? = "") = LocaleException("Language $value is not in the locale manifest", errorMethod, "LC_002")
    fun funExceptionFileNotFound(errorMethod: String, value: String? = "") = LocaleException("Locale file $value not found in resources", errorMethod, "LC_003")
}

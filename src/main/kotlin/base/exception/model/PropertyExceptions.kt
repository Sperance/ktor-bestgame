package base.exception.model

import base.exception.BaseException

object PropertyExceptions {
    open class PropertyException(message: String?, errorMethod: String?, errorCode: String, messageArgs: List<String> = emptyList()) : BaseException(message, "Property", errorMethod, errorCode, messageArgs) {
        override fun toString(): String {
            return "{PropertyException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funException(errorMethod: String, value: String? = "") = PropertyException(value, errorMethod, "PR_001", listOf(value.orEmpty()))
}
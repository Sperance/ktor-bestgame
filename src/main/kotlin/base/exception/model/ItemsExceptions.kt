package base.exception.model

import base.exception.BaseException

object ItemsExceptions {
    open class ItemsException(message: String?, errorMethod: String?, errorCode: String, messageArgs: List<String> = emptyList()) : BaseException(message, "Items", errorMethod, errorCode, messageArgs) {
        override fun toString(): String {
            return "{ItemsException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funException(errorMethod: String, value: String? = "") = ItemsException(value, errorMethod, "IT_001", listOf(value.orEmpty()))
}
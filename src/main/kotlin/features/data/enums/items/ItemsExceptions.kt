package features.data.enums.items

import base.exception.BaseException

object ItemsExceptions {
    open class ItemsException(message: String?, errorMethod: String?, errorCode: String) : BaseException(message, "Items", errorMethod, errorCode) {
        override fun toString(): String {
            return "{ItemsException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funException(errorMethod: String, value: String? = "") = ItemsException(value, errorMethod, "IT_001")
}
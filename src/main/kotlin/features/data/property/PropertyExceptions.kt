package features.data.property

import base.exception.BaseException

object PropertyExceptions {
    open class PropertyException(message: String?, errorMethod: String?, errorCode: String) : BaseException(message, "Property", errorMethod, errorCode) {
        override fun toString(): String {
            return "{PropertyException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funException(errorMethod: String, value: String? = "") = PropertyException(value, errorMethod, "PR_001")
}
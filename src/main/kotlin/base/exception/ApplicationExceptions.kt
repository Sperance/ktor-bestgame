package base.exception

object ApplicationExceptions {
    open class ApplicationException(message: String?, errorMethod: String?, errorCode: String) : BaseException(message, "BaseRoute", errorMethod, errorCode) {
        override fun toString(): String {
            return "{ApplicationException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funException(errorMethod: String, value: String? = "") = ApplicationException(value, errorMethod, "SYS_001")
    fun funExceptionDisconnected(errorMethod: String, value: String? = "") = ApplicationException("Database disconnected", errorMethod, "SYS_002")
    fun funExceptionError(errorMethod: String, value: String? = "") = ApplicationException("Database error $value", errorMethod, "SYS_003")
}
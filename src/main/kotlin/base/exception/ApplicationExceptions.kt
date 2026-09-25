package base.exception

object ApplicationExceptions {
    open class ApplicationException(message: String?, errorMethod: String?, errorCode: String, messageArgs: List<String> = emptyList()) : BaseException(message, "BaseRoute", errorMethod, errorCode, messageArgs) {
        override fun toString(): String {
            return "{ApplicationException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funExceptionDisconnected(errorMethod: String, value: String? = "") = ApplicationException("Database disconnected", errorMethod, "SYS_002", listOf(value.orEmpty()))
    fun funExceptionError(errorMethod: String, value: String? = "") = ApplicationException("Database error $value", errorMethod, "SYS_003", listOf(value.orEmpty()))
}
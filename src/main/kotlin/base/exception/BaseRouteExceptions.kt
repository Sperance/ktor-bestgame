package base.exception

object BaseRouteExceptions {
    open class BaseRouteException(message: String?, errorMethod: String?, errorCode: String, messageArgs: List<String> = emptyList()) : BaseException(message, "BaseRoute", errorMethod, errorCode, messageArgs) {
        override fun toString(): String {
            return "{BaseRouteException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funException(errorMethod: String, value: String? = "") = BaseRouteException(value, errorMethod, "BRT_001", listOf(value.orEmpty()))
    fun funExceptionFormatId(errorMethod: String, value: String? = "") = BaseRouteException("Invalid format of ID '$value'", errorMethod, "BRT_002", listOf(value.orEmpty()))
    fun funExceptionQuery(errorMethod: String, value: String? = "") = BaseRouteException("Missing query parameter '$value'", errorMethod, "BRT_003", listOf(value.orEmpty()))
}
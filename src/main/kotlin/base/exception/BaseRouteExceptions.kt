package base.exception

object BaseRouteExceptions {
    open class BaseRouteException(message: String?, errorMethod: String?, errorCode: String) : BaseException(message, "BaseRoute", errorMethod, errorCode) {
        override fun toString(): String {
            return "{BaseRouteException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funException(errorMethod: String, value: String? = "") = BaseRouteException(value, errorMethod, "BRT_001")
    fun funExceptionFormatId(errorMethod: String, value: String? = "") = BaseRouteException("Invalid format of ID '$value'", errorMethod, "BRT_002")
    fun funExceptionQuery(errorMethod: String, value: String? = "") = BaseRouteException("Missing query parameter '$value'", errorMethod, "BRT_003")
}
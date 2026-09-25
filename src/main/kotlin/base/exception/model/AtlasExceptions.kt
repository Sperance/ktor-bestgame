package base.exception.model

import base.exception.BaseException

object AtlasExceptions {
    open class AtlasException(message: String?, errorMethod: String?, errorCode: String, messageArgs: List<String> = emptyList()) : BaseException(message, "Atlas", errorMethod, errorCode, messageArgs) {
        override fun toString(): String {
            return "{AtlasException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funExceptionContent(errorMethod: String, value: String? = "") = AtlasException("Atlas content is invalid: $value", errorMethod, "AT_001", listOf(value.orEmpty()))
    fun funExceptionNodeNotFound(errorMethod: String, value: String? = "") = AtlasException("Atlas node $value not found", errorMethod, "AT_002", listOf(value.orEmpty()))
    fun funExceptionAlreadyTaken(errorMethod: String, value: String? = "") = AtlasException("Atlas node $value is already allocated", errorMethod, "AT_003", listOf(value.orEmpty()))
    fun funExceptionNotTaken(errorMethod: String, value: String? = "") = AtlasException("Atlas node $value is not allocated", errorMethod, "AT_004", listOf(value.orEmpty()))
    fun funExceptionNotConnected(errorMethod: String, value: String? = "") = AtlasException("Atlas node $value is not connected to any allocated node", errorMethod, "AT_005", listOf(value.orEmpty()))
    fun funExceptionNoPoints(errorMethod: String, value: String? = "") = AtlasException("Character has not enough atlas points ($value)", errorMethod, "AT_006", listOf(value.orEmpty()))
    fun funExceptionWouldDetach(errorMethod: String, value: String? = "") = AtlasException("Refunding $value would detach other atlas nodes from the start", errorMethod, "AT_007", listOf(value.orEmpty()))
    fun funExceptionStart(errorMethod: String, value: String? = "") = AtlasException("Atlas start node $value is always allocated", errorMethod, "AT_008", listOf(value.orEmpty()))
}

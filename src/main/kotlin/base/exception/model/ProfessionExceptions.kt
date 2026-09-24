package base.exception.model

import base.exception.BaseException

object ProfessionExceptions {
    open class ProfessionException(message: String?, errorMethod: String?, errorCode: String, messageArgs: List<String> = emptyList()) : BaseException(message, "Profession", errorMethod, errorCode, messageArgs) {
        override fun toString(): String {
            return "{ProfessionException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funExceptionContent(errorMethod: String, value: String? = "") = ProfessionException("Professions content is invalid: $value", errorMethod, "CF_001", listOf(value.orEmpty()))
    fun funExceptionJobNotFound(errorMethod: String, value: String? = "") = ProfessionException("Work $value not found", errorMethod, "CF_002", listOf(value.orEmpty()))
    fun funExceptionLevel(errorMethod: String, value: String? = "") = ProfessionException("Profession level too low for $value", errorMethod, "CF_003", listOf(value.orEmpty()))
    fun funExceptionNoTool(errorMethod: String, value: String? = "") = ProfessionException("No tool in the $value slot", errorMethod, "CF_004", listOf(value.orEmpty()))
}

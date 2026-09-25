package base.exception.model

import base.exception.BaseException

object ProgressionExceptions {
    open class ProgressionException(message: String?, errorMethod: String?, errorCode: String, messageArgs: List<String> = emptyList()) : BaseException(message, "Progression", errorMethod, errorCode, messageArgs) {
        override fun toString(): String {
            return "{ProgressionException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funExceptionClassCode(errorMethod: String, value: String? = "") = ProgressionException("Character class code '$value' is null or empty", errorMethod, "PR_002", listOf(value.orEmpty()))
    fun funExceptionClassNotFound(errorMethod: String, value: String? = "") = ProgressionException("Character class $value not found", errorMethod, "PR_003", listOf(value.orEmpty()))
    fun funExceptionLevel(errorMethod: String, value: String? = "") = ProgressionException("Level $value must be positive", errorMethod, "PR_004", listOf(value.orEmpty()))
    fun funExceptionExperience(errorMethod: String, value: String? = "") = ProgressionException("Experience $value must not be negative", errorMethod, "PR_005", listOf(value.orEmpty()))
    fun funExceptionNoLevels(errorMethod: String, value: String? = "") = ProgressionException("Experience level table is empty", errorMethod, "PR_006", listOf(value.orEmpty()))
}

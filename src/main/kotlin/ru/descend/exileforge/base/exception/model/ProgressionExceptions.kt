package ru.descend.exileforge.base.exception.model
import ru.descend.exileforge.base.exception.BaseException

object ProgressionExceptions {
    open class ProgressionException(message: String?, errorMethod: String?, errorCode: String, messageArgs: List<String> = emptyList()) : BaseException(message, "Progression", errorMethod, errorCode, messageArgs) {
        override fun toString(): String = "{ProgressionException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
    }

    fun funExceptionClassNotFound(errorMethod: String, value: String? = "") = ProgressionException("Character class $value not found", errorMethod, "PR_003", listOf(value.orEmpty()))
    fun funExceptionLevel(errorMethod: String, value: String? = "") = ProgressionException("Level $value must be positive", errorMethod, "PR_004", listOf(value.orEmpty()))
    fun funExceptionPathStep(errorMethod: String, value: String? = "") = ProgressionException("Path step $value is not done yet", errorMethod, "PR_007", listOf(value.orEmpty()))
    fun funExceptionExperience(errorMethod: String, value: String? = "") = ProgressionException("Experience $value must not be negative", errorMethod, "PR_005", listOf(value.orEmpty()))
}

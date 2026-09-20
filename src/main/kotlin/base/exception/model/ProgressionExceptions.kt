package base.exception.model

import base.exception.BaseException

object ProgressionExceptions {
    open class ProgressionException(message: String?, errorMethod: String?, errorCode: String) : BaseException(message, "Progression", errorMethod, errorCode) {
        override fun toString(): String {
            return "{ProgressionException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funException(errorMethod: String, value: String? = "") = ProgressionException(value, errorMethod, "PR_001")
    fun funExceptionClassCode(errorMethod: String, value: String? = "") = ProgressionException("Character class code '$value' is null or empty", errorMethod, "PR_002")
    fun funExceptionClassNotFound(errorMethod: String, value: String? = "") = ProgressionException("Character class $value not found", errorMethod, "PR_003")
    fun funExceptionLevel(errorMethod: String, value: String? = "") = ProgressionException("Level $value must be positive", errorMethod, "PR_004")
    fun funExceptionExperience(errorMethod: String, value: String? = "") = ProgressionException("Experience $value must not be negative", errorMethod, "PR_005")
    fun funExceptionNoLevels(errorMethod: String, value: String? = "") = ProgressionException("Experience level table is empty", errorMethod, "PR_006")
}

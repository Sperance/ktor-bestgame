package ru.descend.exileforge.base.exception.model
import ru.descend.exileforge.base.exception.BaseException

object QuestExceptions {
    open class QuestException(message: String?, errorMethod: String?, errorCode: String, messageArgs: List<String> = emptyList()) : BaseException(message, "Quest", errorMethod, errorCode, messageArgs) {
        override fun toString(): String = "{QuestException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
    }

    fun funExceptionNotFound(errorMethod: String, value: String? = "") = QuestException("Quest $value not found", errorMethod, "QU_001", listOf(value.orEmpty()))
    fun funExceptionNotDone(errorMethod: String, value: String? = "") = QuestException("Quest $value is not finished", errorMethod, "QU_002", listOf(value.orEmpty()))
    fun funExceptionClaimed(errorMethod: String, value: String? = "") = QuestException("Quest $value is already claimed", errorMethod, "QU_003", listOf(value.orEmpty()))
    fun funExceptionContracts(errorMethod: String, value: String? = "") = QuestException("$value contracts taken, no more allowed", errorMethod, "QU_004", listOf(value.orEmpty()))
    fun funExceptionShare(errorMethod: String, value: String? = "") = QuestException("A share needs a contribution of at least $value", errorMethod, "QU_006", listOf(value.orEmpty()))
    fun funExceptionOffer(errorMethod: String, value: String? = "") = QuestException("Contract notice $value is no longer on the board", errorMethod, "QU_007", listOf(value.orEmpty()))
}

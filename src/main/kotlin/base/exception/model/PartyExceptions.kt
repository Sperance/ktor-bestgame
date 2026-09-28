package base.exception.model

import base.exception.BaseException

object PartyExceptions {
    open class PartyException(message: String?, errorMethod: String?, errorCode: String, messageArgs: List<String> = emptyList()) : BaseException(message, "Party", errorMethod, errorCode, messageArgs) {
        override fun toString(): String {
            return "{PartyException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funExceptionNotFound(errorMethod: String, value: String? = "") = PartyException("Party $value not found", errorMethod, "PT_001", listOf(value.orEmpty()))
    fun funExceptionAlreadyIn(errorMethod: String, value: String? = "") = PartyException("Character is already in party $value", errorMethod, "PT_002", listOf(value.orEmpty()))
    fun funExceptionNotIn(errorMethod: String, value: String? = "") = PartyException("Character $value is not in a party", errorMethod, "PT_003", listOf(value.orEmpty()))
    fun funExceptionFull(errorMethod: String, value: String? = "") = PartyException("Party is full: $value heroes at most", errorMethod, "PT_004", listOf(value.orEmpty()))
    fun funExceptionStarted(errorMethod: String, value: String? = "") = PartyException("Party $value has already set out", errorMethod, "PT_005", listOf(value.orEmpty()))
    fun funExceptionNotHost(errorMethod: String, value: String? = "") = PartyException("Only the party's host can do this: $value", errorMethod, "PT_006", listOf(value.orEmpty()))
    fun funExceptionAlone(errorMethod: String, value: String? = "") = PartyException("Nobody has joined party $value yet", errorMethod, "PT_007", listOf(value.orEmpty()))
    fun funExceptionOffline(errorMethod: String, value: String? = "") = PartyException("Hero $value is not connected", errorMethod, "PT_008", listOf(value.orEmpty()))
}

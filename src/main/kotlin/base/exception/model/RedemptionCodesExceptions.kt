package base.exception.model

import base.exception.BaseException
import base.exception.model.CharacterExceptions.CharacterException

object RedemptionCodesExceptions {
    open class RedemptionCodesException(message: String?, errorMethod: String?, errorCode: String) : BaseException(message, "RedemptionCodes", errorMethod, errorCode) {
        override fun toString(): String {
            return "{RedemptionCodesException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funException(errorMethod: String, value: String? = "") = RedemptionCodesException(value, errorMethod, "RDC_001")
    fun funExceptionNotFoundRedemption(errorMethod: String, value: String? = "") = CharacterException("Redemption code with value $value not found", errorMethod, "RDC_002")
    fun funExceptionRedemptionAlreadyUser(errorMethod: String, value: String? = "") = CharacterException("Redemption code with value $value already used", errorMethod, "RDC_003")
    fun funExceptionRedemptionExpired(errorMethod: String, value: String? = "") = CharacterException("Redemption code with value $value has expired", errorMethod, "RDC_004")
}
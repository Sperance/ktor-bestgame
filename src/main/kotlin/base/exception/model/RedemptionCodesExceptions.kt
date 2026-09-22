package base.exception.model

import base.exception.BaseException
import base.exception.model.CharacterExceptions.CharacterException

object RedemptionCodesExceptions {
    open class RedemptionCodesException(message: String?, errorMethod: String?, errorCode: String, messageArgs: List<String> = emptyList()) : BaseException(message, "RedemptionCodes", errorMethod, errorCode, messageArgs) {
        override fun toString(): String {
            return "{RedemptionCodesException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funException(errorMethod: String, value: String? = "") = RedemptionCodesException(value, errorMethod, "RDC_001", listOf(value.orEmpty()))
    fun funExceptionNotFoundRedemption(errorMethod: String, value: String? = "") = CharacterException("Redemption code with value $value not found", errorMethod, "RDC_002", listOf(value.orEmpty()))
    fun funExceptionRedemptionAlreadyUser(errorMethod: String, value: String? = "") = CharacterException("Redemption code with value $value already used", errorMethod, "RDC_003", listOf(value.orEmpty()))
    fun funExceptionRedemptionExpired(errorMethod: String, value: String? = "") = CharacterException("Redemption code with value $value has expired", errorMethod, "RDC_004", listOf(value.orEmpty()))
    fun funExceptionEmptyTreasure(errorMethod: String, value: String? = "") = RedemptionCodesException("Redemption code $value gives nothing", errorMethod, "RDC_005", listOf(value.orEmpty()))
    fun funExceptionUnknownEquipment(errorMethod: String, value: String? = "") = RedemptionCodesException("Redemption reward names equipment $value that does not exist", errorMethod, "RDC_006", listOf(value.orEmpty()))
    fun funExceptionRewardAmount(errorMethod: String, value: String? = "") = RedemptionCodesException("Redemption reward amount $value must be positive", errorMethod, "RDC_007", listOf(value.orEmpty()))
    fun funExceptionCodeExists(errorMethod: String, value: String? = "") = RedemptionCodesException("Redemption code $value already exists", errorMethod, "RDC_008", listOf(value.orEmpty()))
}
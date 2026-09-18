package base.exception.model

import base.exception.BaseException

object ModifierExceptions {
    open class ModifierException(message: String?, errorMethod: String?, errorCode: String) : BaseException(message, "Modifier", errorMethod, errorCode) {
        override fun toString(): String {
            return "{ModifierException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funException(errorMethod: String, value: String? = "") = ModifierException(value, errorMethod, "MD_001")
    fun funExceptionCode(errorMethod: String, value: String? = "") = ModifierException("Modifier code '$value' is null or empty", errorMethod, "MD_002")
    fun funExceptionNotFound(errorMethod: String, value: String? = "") = ModifierException("ModifierDefinition with id $value not found", errorMethod, "MD_003")
    fun funExceptionCodeNotFound(errorMethod: String, value: String? = "") = ModifierException("ModifierDefinition with code $value not found", errorMethod, "MD_004")
    fun funExceptionTier(errorMethod: String, value: String? = "") = ModifierException("Modifier tier $value must be greater than zero", errorMethod, "MD_005")
    fun funExceptionTierRange(errorMethod: String, value: String? = "") = ModifierException("Modifier tier has invalid value range $value", errorMethod, "MD_006")
    fun funExceptionTierEffects(errorMethod: String, value: String? = "") = ModifierException("Modifier tier value count does not match effect count ($value)", errorMethod, "MD_007")
}

package base.exception.model

import base.exception.BaseException

object ModifierExceptions {
    open class ModifierException(message: String?, errorMethod: String?, errorCode: String, messageArgs: List<String> = emptyList()) : BaseException(message, "Modifier", errorMethod, errorCode, messageArgs) {
        override fun toString(): String {
            return "{ModifierException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funException(errorMethod: String, value: String? = "") = ModifierException(value, errorMethod, "MD_001", listOf(value.orEmpty()))
    fun funExceptionCode(errorMethod: String, value: String? = "") = ModifierException("Modifier code '$value' is null or empty", errorMethod, "MD_002", listOf(value.orEmpty()))
    fun funExceptionNotFound(errorMethod: String, value: String? = "") = ModifierException("ModifierDefinition with id $value not found", errorMethod, "MD_003", listOf(value.orEmpty()))
    fun funExceptionCodeNotFound(errorMethod: String, value: String? = "") = ModifierException("ModifierDefinition with code $value not found", errorMethod, "MD_004", listOf(value.orEmpty()))
    fun funExceptionTier(errorMethod: String, value: String? = "") = ModifierException("Modifier tier $value must be greater than zero", errorMethod, "MD_005", listOf(value.orEmpty()))
    fun funExceptionTierRange(errorMethod: String, value: String? = "") = ModifierException("Modifier tier has invalid value range $value", errorMethod, "MD_006", listOf(value.orEmpty()))
    fun funExceptionNoEffects(errorMethod: String, value: String? = "") = ModifierException("Modifier $value has no effects", errorMethod, "MD_008", listOf(value.orEmpty()))
    fun funExceptionConversionOrder(errorMethod: String, value: String? = "") = ModifierException("Conversion source must be computed before its target ($value)", errorMethod, "MD_009", listOf(value.orEmpty()))
    fun funExceptionConversionAmount(errorMethod: String, value: String? = "") = ModifierException("Conversion step $value must be positive", errorMethod, "MD_010", listOf(value.orEmpty()))
    fun funExceptionLocalConversion(errorMethod: String, value: String? = "") = ModifierException("Local modifier $value cannot be a conversion: it would pull a global stat inside the item", errorMethod, "MD_011", listOf(value.orEmpty()))
    fun funExceptionTierEffects(errorMethod: String, value: String? = "") = ModifierException("Modifier tier value count does not match effect count ($value)", errorMethod, "MD_007", listOf(value.orEmpty()))
}

package base.exception.model

import base.exception.BaseException

object CurrencyExceptions {
    open class CurrencyException(message: String?, errorMethod: String?, errorCode: String, messageArgs: List<String> = emptyList()) : BaseException(message, "Currency", errorMethod, errorCode, messageArgs) {
        override fun toString(): String {
            return "{CurrencyException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funException(errorMethod: String, value: String? = "") = CurrencyException(value, errorMethod, "CR_001", listOf(value.orEmpty()))
    fun funExceptionNotCurrency(errorMethod: String, value: String? = "") = CurrencyException("Item $value is not a currency orb", errorMethod, "CR_002", listOf(value.orEmpty()))
    fun funExceptionNotEnough(errorMethod: String, value: String? = "") = CurrencyException("Character has no $value left", errorMethod, "CR_003", listOf(value.orEmpty()))
    fun funExceptionCorrupted(errorMethod: String, value: String? = "") = CurrencyException("Item $value is corrupted and cannot be modified", errorMethod, "CR_004", listOf(value.orEmpty()))
    fun funExceptionRarity(errorMethod: String, value: String? = "") = CurrencyException("Orb cannot be applied to an item of this rarity ($value)", errorMethod, "CR_005", listOf(value.orEmpty()))
    fun funExceptionNoAffixes(errorMethod: String, value: String? = "") = CurrencyException("Item $value has no affixes to change", errorMethod, "CR_006", listOf(value.orEmpty()))
    fun funExceptionNoFreeAffix(errorMethod: String, value: String? = "") = CurrencyException("Item $value has no free affix slot", errorMethod, "CR_007", listOf(value.orEmpty()))
    fun funExceptionNoImplicits(errorMethod: String, value: String? = "") = CurrencyException("Item $value has no implicit modifiers", errorMethod, "CR_008", listOf(value.orEmpty()))
    fun funExceptionNotForItem(errorMethod: String, value: String? = "") = CurrencyException("Orb $value is not applied to an item", errorMethod, "CR_009", listOf(value.orEmpty()))
    fun funExceptionMirrored(errorMethod: String, value: String? = "") = CurrencyException("Item $value is mirrored and cannot be modified", errorMethod, "CR_010", listOf(value.orEmpty()))
}

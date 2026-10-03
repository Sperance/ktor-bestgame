package base.exception.model

import base.exception.BaseException

object CurrencyExceptions {
    open class CurrencyException(message: String?, errorMethod: String?, errorCode: String, messageArgs: List<String> = emptyList()) : BaseException(message, "Currency", errorMethod, errorCode, messageArgs) {
        override fun toString(): String = "{CurrencyException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
    }

    fun funExceptionNotCurrency(errorMethod: String, value: String? = "") = CurrencyException("Item $value is not a currency orb", errorMethod, "CR_002", listOf(value.orEmpty()))
    fun funExceptionRarity(errorMethod: String, value: String? = "") = CurrencyException("Orb cannot be applied to an item of this rarity ($value)", errorMethod, "CR_005", listOf(value.orEmpty()))
    fun funExceptionRecipeNotFound(errorMethod: String, value: String? = "") = CurrencyException("Bench recipe $value not found", errorMethod, "CR_018", listOf(value.orEmpty()))
}

package base.exception.model

import base.exception.BaseException

object CurrencyExceptions {
    open class CurrencyException(message: String?, errorMethod: String?, errorCode: String) : BaseException(message, "Currency", errorMethod, errorCode) {
        override fun toString(): String {
            return "{CurrencyException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funException(errorMethod: String, value: String? = "") = CurrencyException(value, errorMethod, "CR_001")
    fun funExceptionNotCurrency(errorMethod: String, value: String? = "") = CurrencyException("Item $value is not a currency orb", errorMethod, "CR_002")
    fun funExceptionNotEnough(errorMethod: String, value: String? = "") = CurrencyException("Character has no $value left", errorMethod, "CR_003")
    fun funExceptionCorrupted(errorMethod: String, value: String? = "") = CurrencyException("Item $value is corrupted and cannot be modified", errorMethod, "CR_004")
    fun funExceptionRarity(errorMethod: String, value: String? = "") = CurrencyException("Orb cannot be applied to an item of this rarity ($value)", errorMethod, "CR_005")
    fun funExceptionNoAffixes(errorMethod: String, value: String? = "") = CurrencyException("Item $value has no affixes to change", errorMethod, "CR_006")
    fun funExceptionNoFreeAffix(errorMethod: String, value: String? = "") = CurrencyException("Item $value has no free affix slot", errorMethod, "CR_007")
    fun funExceptionNoImplicits(errorMethod: String, value: String? = "") = CurrencyException("Item $value has no implicit modifiers", errorMethod, "CR_008")
    fun funExceptionNotForItem(errorMethod: String, value: String? = "") = CurrencyException("Orb $value is not applied to an item", errorMethod, "CR_009")
}

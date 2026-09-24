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
    fun funExceptionAlreadyFractured(errorMethod: String, value: String? = "") = CurrencyException("Item $value already has a fractured affix", errorMethod, "CR_011", listOf(value.orEmpty()))
    fun funExceptionTooFewAffixes(errorMethod: String, value: String? = "") = CurrencyException("Item $value needs at least four affixes to fracture", errorMethod, "CR_012", listOf(value.orEmpty()))
    fun funExceptionNotInfluenceable(errorMethod: String, value: String? = "") = CurrencyException("Item $value cannot carry an influence", errorMethod, "CR_013", listOf(value.orEmpty()))
    fun funExceptionAlreadyInfluenced(errorMethod: String, value: String? = "") = CurrencyException("Item $value is already influenced", errorMethod, "CR_014", listOf(value.orEmpty()))
    fun funExceptionAlreadyCrafted(errorMethod: String, value: String? = "") = CurrencyException("Item $value already has a crafted modifier", errorMethod, "CR_015", listOf(value.orEmpty()))
    fun funExceptionGroupTaken(errorMethod: String, value: String? = "") = CurrencyException("Item $value already has a modifier of this group", errorMethod, "CR_016", listOf(value.orEmpty()))
    fun funExceptionNoCrafted(errorMethod: String, value: String? = "") = CurrencyException("Item $value has no crafted modifier", errorMethod, "CR_017", listOf(value.orEmpty()))
    fun funExceptionRecipeNotFound(errorMethod: String, value: String? = "") = CurrencyException("Bench recipe $value not found", errorMethod, "CR_018", listOf(value.orEmpty()))
    fun funExceptionNotMap(errorMethod: String, value: String? = "") = CurrencyException("Orb $value is applied to maps only", errorMethod, "CR_020", listOf(value.orEmpty()))
    fun funExceptionNoHarm(errorMethod: String, value: String? = "") = CurrencyException("Map $value has no harmful affix to change", errorMethod, "CR_021", listOf(value.orEmpty()))
    fun funExceptionAlchemyFull(errorMethod: String, value: String? = "") = CurrencyException("Map $value has no room for this alchemy line", errorMethod, "CR_022", listOf(value.orEmpty()))
    fun funExceptionJewelEmpty(errorMethod: String, value: String? = "") = CurrencyException("Jewel $value would be left without an affix", errorMethod, "CR_023", listOf(value.orEmpty()))
    fun funExceptionRecipeSlot(errorMethod: String, value: String? = "") = CurrencyException("Bench recipe cannot be crafted on $value", errorMethod, "CR_019", listOf(value.orEmpty()))
    fun funExceptionRecipeLocked(errorMethod: String, value: String? = "") = CurrencyException("Bench recipe $value has not been found yet", errorMethod, "CR_024", listOf(value.orEmpty()))
}

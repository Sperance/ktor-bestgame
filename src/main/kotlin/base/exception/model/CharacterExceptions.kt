package base.exception.model

import base.exception.BaseException
import CONST_USER_MAX_CHARACTERS

object CharacterExceptions {
    open class CharacterException(message: String?, errorMethod: String?, errorCode: String, messageArgs: List<String> = emptyList()) : BaseException(message, "Character", errorMethod, errorCode, messageArgs) {
        override fun toString(): String {
            return "{CharacterException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funExceptionName(errorMethod: String, value: String? = "") = CharacterException("Character name is null or empty", errorMethod, "CH_002", listOf(value.orEmpty()))
    fun funExceptionNameDuplicate(errorMethod: String, value: String? = "") = CharacterException("Character with name $value already exists", errorMethod, "CH_003", listOf(value.orEmpty()))
    fun funExceptionUserNotFound(errorMethod: String, value: String? = "") = CharacterException("User with id $value not found", errorMethod, "CH_004", listOf(value.orEmpty()))
    fun funExceptionMaxChars(errorMethod: String, value: String? = "") = CharacterException("User already has maximum amount of characters $CONST_USER_MAX_CHARACTERS", errorMethod, "CH_005", listOf(value.orEmpty()))
    fun funExceptionNotFound(errorMethod: String, value: String? = "") = CharacterException("Character with id $value not found", errorMethod, "CH_006", listOf(value.orEmpty()))
    fun funExceptionEquipmentNotFound(errorMethod: String, value: String? = "") = CharacterException("Equipment with id $value not found", errorMethod, "CH_007", listOf(value.orEmpty()))
    fun funExceptionItemNotFound(errorMethod: String, value: String? = "") = CharacterException("Item with id $value not found", errorMethod, "CH_008", listOf(value.orEmpty()))
    fun funExceptionItemLowZero(errorMethod: String, value: String? = "") = CharacterException("Amount of item $value is less than zero", errorMethod, "CH_009", listOf(value.orEmpty()))
    fun funExceptionItemOverAmount(errorMethod: String, value: String? = "") = CharacterException("Very big amount of item $value", errorMethod, "CH_010", listOf(value.orEmpty()))
    fun funExceptionRequirements(errorMethod: String, value: String? = "") = CharacterException("Equipment requirements are not met ($value)", errorMethod, "CH_013", listOf(value.orEmpty()))
    fun funExceptionSellEquipped(errorMethod: String, value: String? = "") = CharacterException("Item $value must be unequipped before it is sold", errorMethod, "CH_014", listOf(value.orEmpty()))
    fun funExceptionGold(errorMethod: String, value: String? = "") = CharacterException("Not enough gold: $value needed", errorMethod, "CH_016", listOf(value.orEmpty()))
    fun funExceptionOfferNotFound(errorMethod: String, value: String? = "") = CharacterException("The merchant has no offer $value", errorMethod, "CH_017", listOf(value.orEmpty()))
    fun funExceptionMapNotWorn(errorMethod: String, value: String? = "") = CharacterException("Map $value is not worn: it opens a location", errorMethod, "CH_018", listOf(value.orEmpty()))
    fun funExceptionSellSocketed(errorMethod: String, value: String? = "") = CharacterException("Jewel $value must be taken out of its socket before it is sold", errorMethod, "CH_015", listOf(value.orEmpty()))
    fun funExceptionExperience(errorMethod: String, value: String? = "") = CharacterException("Experience amount $value must not be negative", errorMethod, "CH_012", listOf(value.orEmpty()))
}
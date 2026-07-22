package base.exception.model

import base.exception.BaseException
import CONST_USER_MAX_CHARACTERS

object CharacterExceptions {
    open class CharacterException(message: String?, errorMethod: String?, errorCode: String) : BaseException(message, "Character", errorMethod, errorCode) {
        override fun toString(): String {
            return "{CharacterException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funException(errorMethod: String, value: String? = "") = CharacterException(value, errorMethod, "CH_001")
    fun funExceptionName(errorMethod: String, value: String? = "") = CharacterException("Character name is null or empty", errorMethod, "CH_002")
    fun funExceptionNameDuplicate(errorMethod: String, value: String? = "") = CharacterException("Character with name $value already exists", errorMethod, "CH_003")
    fun funExceptionUserNotFound(errorMethod: String, value: String? = "") = CharacterException("User with id $value not found", errorMethod, "CH_004")
    fun funExceptionMaxChars(errorMethod: String, value: String? = "") = CharacterException("User already has maximum amount of characters ${CONST_USER_MAX_CHARACTERS}", errorMethod, "CH_005")
    fun funExceptionNotFound(errorMethod: String, value: String? = "") = CharacterException("Character with id $value not found", errorMethod, "CH_006")
    fun funExceptionEquipmentNotFound(errorMethod: String, value: String? = "") = CharacterException("Equipment with id $value not found", errorMethod, "CH_007")
    fun funExceptionItemNotFound(errorMethod: String, value: String? = "") = CharacterException("Item with id $value not found", errorMethod, "CH_008")
    fun funExceptionItemLowZero(errorMethod: String, value: String? = "") = CharacterException("Amount of item $value is less than zero", errorMethod, "CH_009")
    fun funExceptionItemOverAmount(errorMethod: String, value: String? = "") = CharacterException("Very big amount of item $value", errorMethod, "CH_010")
}
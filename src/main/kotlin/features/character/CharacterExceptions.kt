package features.character

import base.exception.BaseException
import extensions.CONST_USER_MAX_CHARACTERS

object CharacterExceptions {
    open class CharacterException(message: String?, errorMethod: String?, errorCode: String) : BaseException(message, "Character", errorMethod, errorCode) {
        override fun toString(): String {
            return "{CharacterException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funException(errorMethod: String, value: String? = "IllegalStateException") = CharacterException(value, errorMethod, "CH_001")
    fun funExceptionName(errorMethod: String, value: String? = "") = CharacterException("Character name is null or empty", errorMethod, "CH_002")
    fun funExceptionNameDuplicate(errorMethod: String, value: String? = "CharName") = CharacterException("Character with name $value already exists", errorMethod, "CH_003")
    fun funExceptionUserNotFound(errorMethod: String, value: String? = "58935823785") = CharacterException("User with id $value not found", errorMethod, "CH_004")
    fun funExceptionMaxChars(errorMethod: String, value: String? = "") = CharacterException("User already has maximum amount of characters $CONST_USER_MAX_CHARACTERS", errorMethod, "CH_005")
    fun funExceptionNotFound(errorMethod: String, value: String? = "52353253") = CharacterException("Character with id $value not found", errorMethod, "CH_006")
    fun funExceptionItemNotFound(errorMethod: String, value: String? = "53535235") = CharacterException("Equipment with id $value not found", errorMethod, "CH_007")
}
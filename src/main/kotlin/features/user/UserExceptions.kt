package features.user

import base.exception.BaseException

object UserExceptions {
    open class UserException(message: String?, errorMethod: String?, errorCode: String) : BaseException(message, "User", errorMethod, errorCode) {
        override fun toString(): String {
            return "{UserException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funException(errorMethod: String, value: String? = "") = UserException(value, errorMethod, "US_001")
    fun funExceptionInvalidEmail(errorMethod: String, value: String? = "") = UserException("Invalid email $value", errorMethod, "US_002")
    fun funExceptionInvalidAge(errorMethod: String, value: String? = "") = UserException("Invalid age $value", errorMethod, "US_003")
    fun funExceptionInvalidPassword(errorMethod: String, value: String? = "") = UserException("Invalid password $value", errorMethod, "US_004")
    fun funExceptionSalt(errorMethod: String, value: String? = "") = UserException("Field 'salt' blocked to modify", errorMethod, "US_005")
    fun funExceptionLoginExists(errorMethod: String, value: String? = "") = UserException("Login $value already exists", errorMethod, "US_006")
    fun funExceptionEmailExists(errorMethod: String, value: String? = "") = UserException("Email $value already exists", errorMethod, "US_007")

    fun funExceptionPasswordEmpty(errorMethod: String, value: String? = "") = UserException("Password must be not empty", errorMethod, "US_008")
    fun funExceptionPasswordLength(errorMethod: String, value: String? = "") = UserException("Password length is invalid: ${value?.length}", errorMethod, "US_008")
    fun funExceptionPasswordOneDigit(errorMethod: String, value: String? = "") = UserException("Password should contain at least one digit", errorMethod, "US_008")
    fun funExceptionPasswordOneUppercase(errorMethod: String, value: String? = "") = UserException("Password should contains uppercase letter", errorMethod, "US_008")
    fun funExceptionPasswordWhitespace(errorMethod: String, value: String? = "") = UserException("Password shouldn't have whitespace", errorMethod, "US_008")
    fun funExceptionPasswordCheck(errorMethod: String, value: String? = "") = UserException("Invalid password check", errorMethod, "US_009")

    fun funExceptionPasswordLoginPass(errorMethod: String, value: String? = "") = UserException("Invalid login or password", errorMethod, "US_010")
    fun funExceptionInactive(errorMethod: String, value: String? = "") = UserException("Account with login $value is inactive", errorMethod, "US_011")
    fun funExceptionFoundUserId(errorMethod: String, value: String? = "") = UserException("User with id $value not found", errorMethod, "US_012")
}
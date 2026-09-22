package base.exception.model

import base.exception.BaseException

object UserExceptions {
    open class UserException(message: String?, errorMethod: String?, errorCode: String, messageArgs: List<String> = emptyList()) : BaseException(message, "User", errorMethod, errorCode, messageArgs) {
        override fun toString(): String {
            return "{UserException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funException(errorMethod: String, value: String? = "") = UserException(value, errorMethod, "US_001", listOf(value.orEmpty()))
    fun funExceptionInvalidEmail(errorMethod: String, value: String? = "") = UserException("Invalid email $value", errorMethod, "US_002", listOf(value.orEmpty()))
    fun funExceptionInvalidAge(errorMethod: String, value: String? = "") = UserException("Invalid age $value", errorMethod, "US_003", listOf(value.orEmpty()))
    fun funExceptionInvalidPassword(errorMethod: String, value: String? = "") = UserException("Invalid password $value", errorMethod, "US_004", listOf(value.orEmpty()))
    fun funExceptionSalt(errorMethod: String, value: String? = "") = UserException("Field 'salt' blocked to modify", errorMethod, "US_005", listOf(value.orEmpty()))
    fun funExceptionLoginExists(errorMethod: String, value: String? = "") = UserException("Login $value already exists", errorMethod, "US_006", listOf(value.orEmpty()))
    fun funExceptionEmailExists(errorMethod: String, value: String? = "") = UserException("Email $value already exists", errorMethod, "US_007", listOf(value.orEmpty()))

    fun funExceptionPasswordEmpty(errorMethod: String, value: String? = "") = UserException("Password must be not empty", errorMethod, "US_008", listOf(value.orEmpty()))
    fun funExceptionPasswordLength(errorMethod: String, value: String? = "") = UserException("Password length is invalid: ${value?.length}", errorMethod, "US_008", listOf(value.orEmpty()))
    fun funExceptionPasswordOneDigit(errorMethod: String, value: String? = "") = UserException("Password should contain at least one digit", errorMethod, "US_008", listOf(value.orEmpty()))
    fun funExceptionPasswordOneUppercase(errorMethod: String, value: String? = "") = UserException("Password should contains uppercase letter", errorMethod, "US_008", listOf(value.orEmpty()))
    fun funExceptionPasswordWhitespace(errorMethod: String, value: String? = "") = UserException("Password shouldn't have whitespace", errorMethod, "US_008", listOf(value.orEmpty()))
    fun funExceptionPasswordCheck(errorMethod: String, value: String? = "") = UserException("Invalid password check", errorMethod, "US_009", listOf(value.orEmpty()))

    fun funExceptionPasswordLoginPass(errorMethod: String, value: String? = "") = UserException("Invalid login or password", errorMethod, "US_010", listOf(value.orEmpty()))
    fun funExceptionInactive(errorMethod: String, value: String? = "") = UserException("Account with login $value is inactive", errorMethod, "US_011", listOf(value.orEmpty()))
    fun funExceptionFoundUserId(errorMethod: String, value: String? = "") = UserException("User with id $value not found", errorMethod, "US_012", listOf(value.orEmpty()))
    fun funExceptionDoubleDevice(errorMethod: String, value: String? = "") = UserException("User with device $value already exists", errorMethod, "US_013", listOf(value.orEmpty()))
    fun funExceptionEmptyDevice(errorMethod: String, value: String? = "") = UserException("Field `deviceId` is empty", errorMethod, "US_014", listOf(value.orEmpty()))
    fun funExceptionDeviceNotFound(errorMethod: String, value: String? = "") = UserException("User with deviceId $value not found", errorMethod, "US_015", listOf(value.orEmpty()))
}
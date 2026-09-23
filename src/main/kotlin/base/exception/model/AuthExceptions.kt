package base.exception.model

import base.exception.BaseException

/**
 * Отказы в доступе. Код ответа у них свой - 401 или 403, см. [status].
 */
object AuthExceptions {
    open class AuthException(message: String?, errorMethod: String?, errorCode: String, messageArgs: List<String> = emptyList(), val status: Int) :
        BaseException(message, "Auth", errorMethod, errorCode, messageArgs) {
        override fun toString(): String =
            "{AuthException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
    }

    fun funExceptionNoToken(errorMethod: String, value: String? = "") = AuthException("Sign-in required", errorMethod, "AUTH_001", listOf(value.orEmpty()), 401)
    fun funExceptionBadToken(errorMethod: String, value: String? = "") = AuthException("The session has expired or is not valid", errorMethod, "AUTH_002", listOf(value.orEmpty()), 401)
    fun funExceptionAdminOnly(errorMethod: String, value: String? = "") = AuthException("Administrator rights required for $value", errorMethod, "AUTH_003", listOf(value.orEmpty()), 403)
    fun funExceptionNotYourCharacter(errorMethod: String, value: String? = "") = AuthException("Character $value belongs to another account", errorMethod, "AUTH_004", listOf(value.orEmpty()), 403)
    fun funExceptionNotYourAccount(errorMethod: String, value: String? = "") = AuthException("Account $value is not yours", errorMethod, "AUTH_005", listOf(value.orEmpty()), 403)
}

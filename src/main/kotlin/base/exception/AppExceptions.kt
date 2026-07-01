package base.exception

sealed class AppException(
    override val message: String?,
    val httpCode: Int,
    val errorCode: String = ""
) : RuntimeException(message)

class ExceptionForCode(message: String?, code: String): AppException(message, 401, code)
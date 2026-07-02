package base.exception

import kotlinx.serialization.Serializable

@Serializable
open class AppException(
    override val message: String?,
    val httpCode: Int,
    val errorClass: String,
    val errorMethod: String?,
    val errorCode: String = ""
) : RuntimeException(message)

@Serializable
open class BaseException(
    override val message: String?,
    val errorClass: String,
    val errorMethod: String?,
    val errorCode: String
) : RuntimeException(message)
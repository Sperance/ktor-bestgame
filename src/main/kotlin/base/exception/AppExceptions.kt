package base.exception

import kotlinx.serialization.Serializable

@Serializable
open class BaseException(
    override val message: String?,
    val errorClass: String,
    val errorMethod: String?,
    val errorCode: String
) : RuntimeException(message)
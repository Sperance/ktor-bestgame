package ru.descend.shared.error

import kotlinx.serialization.Serializable

@Serializable
@kotlinx.serialization.SerialName("base.exception.BaseException")
open class BaseException(
    override val message: String?,
    val errorClass: String,
    val errorMethod: String?,
    val errorCode: String
) : RuntimeException(message)

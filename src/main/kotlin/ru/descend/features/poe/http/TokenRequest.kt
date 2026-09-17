package ru.descend.features.poe.http

import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
@kotlinx.serialization.SerialName("features.poe.TokenRequest")
data class TokenRequest(val login: String, val password: String)

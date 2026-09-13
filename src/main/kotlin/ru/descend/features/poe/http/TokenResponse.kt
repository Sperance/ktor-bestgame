package ru.descend.features.poe.http

import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
@kotlinx.serialization.SerialName("features.poe.TokenResponse")
data class TokenResponse(val token: String, val expiresIn: Int = 3600)

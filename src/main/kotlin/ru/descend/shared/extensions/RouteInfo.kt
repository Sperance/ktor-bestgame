package ru.descend.shared.extensions

import kotlinx.serialization.Serializable

@Serializable
@kotlinx.serialization.SerialName("extensions.RouteInfo")
data class RouteInfo(
    val path: String,
    val method: String
)

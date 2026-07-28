package extensions

import kotlinx.serialization.Serializable

@Serializable
data class RouteInfo(
    val path: String,
    val method: String
)
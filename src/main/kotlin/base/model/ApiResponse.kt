package base.model

import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
    val errors: List<String>? = null
) {
    companion object {
        fun <T> ok(data: T, message: String? = null) =
            ApiResponse(success = true, data = data, message = message)

        fun <T> created(data: T) =
            ApiResponse(success = true, data = data, message = "Created")

        fun message(text: String) =
            ApiResponse<Unit>(success = true, message = text)

        fun error(message: String, errors: List<String>? = null) =
            ApiResponse<Unit>(success = false, message = message, errors = errors)
    }
}

@Serializable
data class PagedResponse<T>(
    val items: List<T>,
    val page: Int,
    val pageSize: Int,
    val totalItems: Long,
    val totalPages: Int
)

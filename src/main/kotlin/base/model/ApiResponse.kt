package base.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
    val code: String? = null
) {
    companion object {

        fun <T> ok(data: T?, message: String? = null) =
            ApiResponse(success = true, data = data, code = "200", message = message)

        fun <T> created(data: T) =
            ApiResponse(success = true, data = data, code = "201", message = "Created")

        fun message(text: String) =
            ApiResponse<Unit>(success = true, code = "202", message = text)

        fun error(message: String?, code: Int) =
            ApiResponse<Unit>(success = false, message = message, code = code.toString())

        fun error(message: String?, code: String) =
            ApiResponse<Unit>(success = false, message = message, code = code)
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

fun <T> apiResponseSerializer(dataSerializer: KSerializer<T>): KSerializer<ApiResponse<T>> =
    ApiResponse.serializer(dataSerializer)

fun <T> apiResponseListSerializer(itemSerializer: KSerializer<T>): KSerializer<ApiResponse<List<T>>> =
    ApiResponse.serializer(ListSerializer(itemSerializer))

fun <T> apiResponsePagedSerializer(itemSerializer: KSerializer<T>): KSerializer<ApiResponse<PagedResponse<T>>> =
    ApiResponse.serializer(PagedResponse.serializer(itemSerializer))

val apiResponseUnitSerializer: KSerializer<ApiResponse<Unit>> =
    ApiResponse.serializer(Unit.serializer())

val apiResponseMapSerializer: KSerializer<ApiResponse<Map<String, Long>>> =
    ApiResponse.serializer(MapSerializer(String.serializer(), Long.serializer()))
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

// ==================== Фабрики сериализаторов ====================

/**
 * Строит KSerializer<ApiResponse<T>> для конкретного T.
 * Используется в BaseRoute чтобы обойти type erasure.
 */
fun <T> apiResponseSerializer(dataSerializer: KSerializer<T>): KSerializer<ApiResponse<T>> =
    ApiResponse.serializer(dataSerializer)

fun <T> apiResponseListSerializer(itemSerializer: KSerializer<T>): KSerializer<ApiResponse<List<T>>> =
    ApiResponse.serializer(ListSerializer(itemSerializer))

fun <T> apiResponsePagedSerializer(itemSerializer: KSerializer<T>): KSerializer<ApiResponse<PagedResponse<T>>> =
    ApiResponse.serializer(PagedResponse.serializer(itemSerializer))

/** Для ApiResponse<Unit> (сообщения без данных) */
val apiResponseUnitSerializer: KSerializer<ApiResponse<Unit>> =
    ApiResponse.serializer(Unit.serializer())

/** Для ApiResponse<Map<String, Long>> (count и т.п.) */
val apiResponseMapSerializer: KSerializer<ApiResponse<Map<String, Long>>> =
    ApiResponse.serializer(MapSerializer(String.serializer(), Long.serializer()))
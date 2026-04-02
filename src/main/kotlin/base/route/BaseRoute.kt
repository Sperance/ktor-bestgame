package base.route

import base.exception.BadRequestException
import base.model.ApiResponse
import base.model.BaseEntity
import base.model.CreateRequest
import base.model.PagedResponse
import base.model.UpdateRequest
import base.service.BaseService
import base.table.BaseTable
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.text.toIntOrNull
import kotlin.text.toLongOrNull

/**
 * Один наследник = один REST-ресурс.
 *
 * Регистрирует стандартные CRUD + пагинацию + count.
 * Хук [additionalRoutes] — для кастомных эндпоинтов.
 *
 * ВАЖНО: additionalRoutes регистрируются ПЕРЕД /{id},
 *         иначе /active, /search и т.д. перехватятся как {id}.
 */
abstract class BaseRoute<E : BaseEntity, CQ : CreateRequest, UQ : UpdateRequest>(
    protected val service: BaseService<E, CQ, UQ, out BaseTable>,
    protected val basePath: String
) {

    // ========== Сериализаторы — переопределяются через reified-обёртку ==========

    /** Сериализатор для ОДНОЙ сущности E */
    protected abstract val entitySerializer: KSerializer<E>

    protected abstract suspend fun deserializeCreate(call: ApplicationCall): CQ
    protected abstract suspend fun deserializeUpdate(call: ApplicationCall): UQ

    // ========== Вспомогательный JSON (можно вынести в DI / companion) ==========

    protected open val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    // ========== Производные сериализаторы (вычисляются один раз) ==========

    /** ApiResponse<E> */
    private val apiResponseEntitySerializer: KSerializer<ApiResponse<E>> by lazy {
        ApiResponse.serializer(entitySerializer)
    }

    /** ApiResponse<List<E>> */
    private val apiResponseListSerializer: KSerializer<ApiResponse<List<E>>> by lazy {
        ApiResponse.serializer(ListSerializer(entitySerializer))
    }

    /** ApiResponse<PagedResponse<E>> */
    private val apiResponsePagedSerializer: KSerializer<ApiResponse<PagedResponse<E>>> by lazy {
        ApiResponse.serializer(PagedResponse.serializer(entitySerializer))
    }

    /** ApiResponse<Unit> (для message-ответов) */
    private val apiResponseUnitSerializer: KSerializer<ApiResponse<Unit>> by lazy {
        ApiResponse.serializer(Unit.serializer())
    }

    // ========== Типизированный respond ==========

    /**
     * Отправляет JSON-ответ с ЯВНЫМ сериализатором — обходим type erasure.
     */
    protected suspend fun <R> ApplicationCall.respondJson(
        statusCode: HttpStatusCode,
        serializer: KSerializer<R>,
        body: R
    ) {
        val text = json.encodeToString(serializer, body)
        respondText(text, ContentType.Application.Json, statusCode)
    }

    // ========== Регистрация ==========

    fun register(routing: Routing) {
        routing.route(basePath) {
            additionalRoutes(this)
            pagedRoute()
            countRoute()
            getAllRoute()
            getByIdRoute()
            createRoute()
            updateRoute()
            deleteRoute()
            deleteWithVersionRoute()
        }
    }

    // ==================== CRUD routes ====================

    private fun Route.getAllRoute() = get {
        call.respondJson(
            HttpStatusCode.OK,
            apiResponseListSerializer,
            ApiResponse.ok(service.findAll())
        )
    }

    private fun Route.getByIdRoute() = get("/{id}") {
        call.respondJson(
            HttpStatusCode.OK,
            apiResponseEntitySerializer,
            ApiResponse.ok(service.getById(call.longParam("id")))
        )
    }

    private fun Route.createRoute() = post {
        val created = service.create(deserializeCreate(call))
        call.respondJson(
            HttpStatusCode.Created,
            apiResponseEntitySerializer,
            ApiResponse.created(created)
        )
    }

    private fun Route.updateRoute() = put("/{id}") {
        val id = call.longParam("id")
        val updated = service.update(id, deserializeUpdate(call))
        call.respondJson(
            HttpStatusCode.OK,
            apiResponseEntitySerializer,
            ApiResponse.ok(updated, "Updated")
        )
    }

    private fun Route.deleteRoute() = delete("/{id}") {
        service.delete(call.longParam("id"))
        call.respondJson(
            HttpStatusCode.OK,
            apiResponseUnitSerializer,
            ApiResponse.message("Deleted")
        )
    }

    private fun Route.deleteWithVersionRoute() = delete("/{id}/version/{version}") {
        val id = call.longParam("id")
        val version = call.longParam("version")
        service.deleteWithVersion(id, version)
        call.respondJson(
            HttpStatusCode.OK,
            apiResponseUnitSerializer,
            ApiResponse.message("Deleted")
        )
    }

    private fun Route.pagedRoute() = get("/paged") {
        val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
        val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 20
        call.respondJson(
            HttpStatusCode.OK,
            apiResponsePagedSerializer,
            ApiResponse.ok(service.findPaged(page, size))
        )
    }

    private fun Route.countRoute() = get("/count") {
        val countSerializer = ApiResponse.serializer(
            MapSerializer(String.serializer(), Long.serializer())
        )
        call.respondJson(
            HttpStatusCode.OK,
            countSerializer,
            ApiResponse.ok(mapOf("count" to service.count()))
        )
    }

    // ==================== Hook ====================

    protected open fun additionalRoutes(route: Route): Route = route

    // ==================== Utilities ====================

    protected fun ApplicationCall.longParam(name: String): Long =
        parameters[name]?.toLongOrNull()
            ?: throw BadRequestException("Invalid or missing path parameter '$name'")

    protected fun ApplicationCall.queryParam(name: String): String =
        request.queryParameters[name]
            ?: throw BadRequestException("Missing query parameter '$name'")

    protected fun ApplicationCall.optionalQueryParam(name: String): String? =
        request.queryParameters[name]
}
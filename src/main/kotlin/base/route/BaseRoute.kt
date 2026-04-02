package base.route

import base.exception.BadRequestException
import base.model.ApiResponse
import base.model.BaseEntity
import base.model.PagedResponse
import base.model.apiResponseListSerializer
import base.model.apiResponseMapSerializer
import base.model.apiResponsePagedSerializer
import base.model.apiResponseSerializer
import base.model.apiResponseUnitSerializer
import base.service.BaseService
import base.table.BaseTable
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import server.addons.AppJson
import kotlin.text.toIntOrNull
import kotlin.text.toLongOrNull

/**
 * Базовый роут с CRUD.
 *
 * Каждый наследник обязан передать [entitySerializer] — это решает
 * проблему type erasure при сериализации generic ApiResponse<E>.
 *
 * @param E — Entity
 * @param T — Table
 */
abstract class BaseRoute<E : BaseEntity, T : BaseTable>(
    protected val service: BaseService<E, T>,
    protected val basePath: String,
    entitySerializer: KSerializer<E>
) {

    // ========== Предвычисленные сериализаторы (создаются один раз) ==========

    private val singleResponseSerializer: KSerializer<ApiResponse<E>> =
        apiResponseSerializer(entitySerializer)

    private val listResponseSerializer: KSerializer<ApiResponse<List<E>>> =
        apiResponseListSerializer(entitySerializer)

    private val pagedResponseSerializer: KSerializer<ApiResponse<PagedResponse<E>>> =
        apiResponsePagedSerializer(entitySerializer)

    // ==================== Регистрация ====================

    fun register(routing: Routing) {
        routing.route(basePath) {
            // Кастомные маршруты ПЕРВЫМИ (до /{id})
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

    // ==================== CRUD ====================

    private fun Route.getAllRoute() = get {
        val items = service.findAll()
        val response = ApiResponse.ok(items)
        call.respond(HttpStatusCode.OK, listResponseSerializer, response)
    }

    private fun Route.getByIdRoute() = get("/{id}") {
        val entity = service.getById(call.longParam("id"))
        val response = ApiResponse.ok(entity)
        call.respond(HttpStatusCode.OK, singleResponseSerializer, response)
    }

    private fun Route.createRoute() = post {
        val json = call.receive<JsonObject>()
        val created = service.create(json)
        val response = ApiResponse.created(created)
        call.respond(HttpStatusCode.Created, singleResponseSerializer, response)
    }

    private fun Route.updateRoute() = put("/{id}") {
        val id = call.longParam("id")
        val json = call.receive<JsonObject>()
        val updated = service.update(id, json)
        val response = ApiResponse.ok(updated, "Updated")
        call.respond(HttpStatusCode.OK, singleResponseSerializer, response)
    }

    private fun Route.deleteRoute() = delete("/{id}") {
        service.delete(call.longParam("id"))
        val response = ApiResponse.message("Deleted")
        call.respond(HttpStatusCode.OK, apiResponseUnitSerializer, response)
    }

    private fun Route.deleteWithVersionRoute() = delete("/{id}/version/{version}") {
        val id = call.longParam("id")
        val version = call.longParam("version")
        service.deleteWithVersion(id, version)
        val response = ApiResponse.message("Deleted")
        call.respond(HttpStatusCode.OK, apiResponseUnitSerializer, response)
    }

    private fun Route.pagedRoute() = get("/paged") {
        val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
        val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 20
        val paged = service.findPaged(page, size)
        val response = ApiResponse.ok(paged)
        call.respond(HttpStatusCode.OK, pagedResponseSerializer, response)
    }

    private fun Route.countRoute() = get("/count") {
        val count = service.count()
        val response = ApiResponse.ok(mapOf("count" to count))
        call.respond(HttpStatusCode.OK, apiResponseMapSerializer, response)
    }

    // ==================== Hook ====================

    protected open fun additionalRoutes(route: Route): Route {
        TODO()
    }

    // ==================== Utilities ====================

    protected fun ApplicationCall.longParam(name: String): Long =
        parameters[name]?.toLongOrNull()
            ?: throw BadRequestException("Invalid or missing '$name'")

    protected fun ApplicationCall.queryParam(name: String): String =
        request.queryParameters[name]
            ?: throw BadRequestException("Missing query parameter '$name'")

    /**
     * Хелпер для additionalRoutes — respond с явным сериализатором для единичной entity.
     */
    protected suspend fun ApplicationCall.respondEntity(entity: E, status: HttpStatusCode = HttpStatusCode.OK) {
        respond(status, singleResponseSerializer, ApiResponse.ok(entity))
    }

    /**
     * Хелпер для additionalRoutes — respond со списком entities.
     */
    protected suspend fun ApplicationCall.respondEntityList(list: List<E>, status: HttpStatusCode = HttpStatusCode.OK) {
        respond(status, listResponseSerializer, ApiResponse.ok(list))
    }

    /**
     * Хелпер — respond с произвольным типом + его сериализатор.
     */
    protected suspend fun <R> ApplicationCall.respondTyped(
        serializer: KSerializer<ApiResponse<R>>,
        data: R,
        message: String? = null,
        status: HttpStatusCode = HttpStatusCode.OK
    ) {
        respond(status, serializer, ApiResponse.ok(data, message))
    }
}

/**
 * Extension для ApplicationCall — respond с явным сериализатором.
 * Ktor из коробки имеет respond(status, message) но не respond(status, serializer, value).
 */
suspend fun <T> ApplicationCall.respond(
    status: HttpStatusCode,
    serializer: KSerializer<T>,
    value: T
) {
    val text = AppJson.encodeToString(serializer, value)
    respondText(text, ContentType.Application.Json, status)
}
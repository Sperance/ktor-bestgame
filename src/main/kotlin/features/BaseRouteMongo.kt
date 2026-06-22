package features

import base.exception.BadRequestException
import base.exception.ExceptionForCode
import base.model.ApiResponse
import base.model.PagedResponse
import base.model.apiResponseListSerializer
import base.model.apiResponseMapSerializer
import base.model.apiResponsePagedSerializer
import base.model.apiResponseSerializer
import base.model.apiResponseUnitSerializer
import base.service.BaseService
import config.MongoFactory.transactionExecute
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import org.bson.types.ObjectId
import server.addons.AppJson
import kotlin.text.toIntOrNull
import kotlin.text.toLongOrNull

abstract class BaseRouteMongo<T : VersionedEntity>(
    protected val service: BaseServiceMongo<T>,
    protected val basePath: String,
    val entitySerializer: KSerializer<T>
) {

    private val singleResponseSerializer: KSerializer<ApiResponse<T>> = apiResponseSerializer(entitySerializer)
    private val listResponseSerializer: KSerializer<ApiResponse<List<T>>> = apiResponseListSerializer(entitySerializer)
    private val pagedResponseSerializer: KSerializer<ApiResponse<PagedResponse<T>>> = apiResponsePagedSerializer(entitySerializer)

    @OptIn(ExperimentalKtorApi::class)
    fun register(routing: Routing) {
        routing.route(basePath) {
            // Кастомные маршруты ПЕРВЫМИ (до /{id})
            additionalRoutes(this)

            pagedRoute()
            countRoute()
            getAllRoute()
            createRoute()
            updateRoute()
            deleteRoute()
        }.describe {
            tag(this@BaseRouteMongo.basePath.substringAfterLast("/"))
        }
    }

    private fun Route.getAllRoute() = get {
        val id = call.request.queryParameters["id"]
        if (id == null) {
            val items = service.findAll()
            val response = ApiResponse.ok(items)
            call.respond(HttpStatusCode.OK, listResponseSerializer, response)
        } else {
            if (id.length != 24) {
                throw ExceptionForCode("Неверный формат ID. Длина: ${id.length} Должна быть: 24", "BRM_GETALL_ID")
            }
            val entity = service.findById(id)
            val response = ApiResponse.ok(entity)
            call.respond(HttpStatusCode.OK, singleResponseSerializer, response)
        }
    }

    private fun Route.createRoute() = post {
        val json = call.receive<JsonObject>()
        val entity = AppJson.decodeFromJsonElement(entitySerializer, json)
        val created = transactionExecute("[${basePath}::createRoute] $entity") { session ->
            service.create(entity, session)
        }
        val response = ApiResponse.created(created)
        call.respond(HttpStatusCode.Created, singleResponseSerializer, response)
    }

    private fun Route.updateRoute() = put {
        val id = call.queryParam("id")
        val json = call.receive<JsonObject>()
        val entity = AppJson.decodeFromJsonElement(entitySerializer, json)
        entity._id = ObjectId(id)
        val updated = transactionExecute("[${basePath}::updateRoute] $entity") { session ->
            service.update(entity, session)
        }
        val response = ApiResponse.ok(updated, "Updated")
        call.respond(HttpStatusCode.OK, singleResponseSerializer, response)
    }

    private fun Route.deleteRoute() = delete {
        service.delete(call.queryParam("id"))
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

    protected open fun additionalRoutes(route: Route): Route {
        return route
    }

    protected fun ApplicationCall.longParam(name: String): Long =
        parameters[name]?.toLongOrNull()
            ?: throw BadRequestException("Invalid or missing '$name'")

    protected fun ApplicationCall.queryParam(name: String): String =
        request.queryParameters[name] ?: throw BadRequestException("Missing query parameter '$name'")

    protected fun ApplicationCall.queryParam(name: String, default: String = ""): String =
        request.queryParameters[name] ?: default

    protected suspend fun ApplicationCall.respondEntity(entity: T?, status: HttpStatusCode = HttpStatusCode.OK) {
        respond(status, singleResponseSerializer, ApiResponse.ok(entity))
    }

    protected suspend fun ApplicationCall.respondEntityList(list: List<T>, status: HttpStatusCode = HttpStatusCode.OK) {
        respond(status, listResponseSerializer, ApiResponse.ok(list))
    }

    protected suspend fun <R> ApplicationCall.respondTyped(
        serializer: KSerializer<ApiResponse<R>>,
        data: R,
        message: String? = null,
        status: HttpStatusCode = HttpStatusCode.OK
    ) {
        respond(status, serializer, ApiResponse.ok(data, message))
    }
}

suspend fun <T> ApplicationCall.respond(
    status: HttpStatusCode,
    serializer: KSerializer<T>,
    value: T
) {
    val text = AppJson.encodeToString(serializer, value)
    respondText(text, ContentType.Application.Json, status)
}
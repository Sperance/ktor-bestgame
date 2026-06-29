package base.route

import base.exception.ExceptionForCode
import base.model.PagedResponse
import base.repository.BaseRepositoryMongo
import config.MongoFactory.transactionExecute
import extensions.CONST_SYSTEM_FIELDS
import features.VersionedEntity
import features.userMongo.UserMongo
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import server.addons.AppJson
import kotlin.reflect.typeOf
import kotlin.text.toIntOrNull

abstract class BaseRouteMongo<T : VersionedEntity, R>(
    protected val repository: BaseRepositoryMongo<T>,
    protected val basePath: String,
    val entitySerializer: KSerializer<T>,
    val responseSerializer: KSerializer<R>,
    private val toResponse: (T) -> R
) {
    private val apiResponseSerializer = ApiMongoResponse.serializer(responseSerializer)
    private val apiResponseListSerializer = ApiMongoResponse.serializer(ListSerializer(responseSerializer))
    private val apiResponsePagedSerializer = ApiMongoResponse.serializer(PagedResponse.serializer(responseSerializer))

    @OptIn(ExperimentalKtorApi::class)
    fun register(routing: Routing) {
        routing.route(basePath) {
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
        val id = call.queryParam("id", "")
        if (id == "") {
            val items = repository.findAll().map { toResponse(it) }
            call.respond(apiResponseListSerializer, ApiMongoResponse.ok(items))
        } else {
            if (id.length != 24) {
                throw ExceptionForCode("Неверный формат ID. Длина: ${id.length} Должна быть: 24", "BRM_GETALL_ID")
            }

            val entity = repository.findById(id)
            val responseEntity = entity?.let(toResponse)
            call.respond(apiResponseSerializer, ApiMongoResponse.ok(responseEntity))
        }
    }

    private fun Route.createRoute() = post {
       try {
            val jsonList = call.receive<JsonArray>()
            val entity = AppJson.decodeFromJsonElement(ListSerializer(entitySerializer), jsonList)
            val created = transactionExecute("[${basePath}::createRoute] $entity") { session ->
                repository.insertMany(entity, session)
            }.map { toResponse(it) }
           call.respond(apiResponseListSerializer, ApiMongoResponse.ok(created))
        } catch (e: Exception) {
            throw ExceptionForCode(e.message, "BRM_CREATE_EXCEPTION")
        }
    }

    private fun Route.updateRoute() = put {
        try {
            val id = call.idParam()
            val json = call.receive<JsonObject>()

            // Преобразуем JSON в Map, исключая служебные поля
            val updates = json.entries
                .filter { it.key !in CONST_SYSTEM_FIELDS }
                .associate { it.key to jsonElementToNative(it.value) }

            // Вызываем специальный метод для частичного обновления
            val updated = transactionExecute("[${basePath}::updateRoute] $id") { session ->
                repository.updateFields(id, updates, session)
            }?.let { toResponse(it) }

            call.respond(apiResponseSerializer, ApiMongoResponse.ok(updated, "Updated"))
        } catch (e: Exception) {
            throw ExceptionForCode(e.message, "BRM_UPDATE_EXCEPTION")
        }
    }

    private fun Route.deleteRoute() = delete {
        try {
            val id = call.idParam()
            transactionExecute("[${basePath}]::deleteRoute $id") { session ->
                repository.deleteById(id, session)
            }

            call.respond(ApiMongoResponse.ok("Deleted"))
        } catch (e: Exception) {
            throw ExceptionForCode(e.message, "BRM_DELETE_EXCEPTION")
        }
    }

    private fun Route.pagedRoute() = get("/paged") {
        try {
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
            val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 20
            val paged = repository.findPaged(page, size)

            // Преобразуем элементы внутри PagedResponse из T в R
            val responseItems = PagedResponse(
                items = paged.items.map { toResponse(it) },
                page = paged.page,
                pageSize = paged.pageSize,
                totalItems = paged.totalItems,
                totalPages = paged.totalPages
            )

            call.respond(apiResponsePagedSerializer, ApiMongoResponse.ok(responseItems))
        } catch (e: Exception) {
            throw ExceptionForCode(e.message, "BRM_PAGED_EXCEPTION")
        }
    }

    private fun Route.countRoute() = get("/count") {
        val count = repository.count()
        call.respond(ApiMongoResponse.ok(mapOf("count" to count)))
    }

    protected open fun additionalRoutes(route: Route): Route {
        return route
    }

    protected fun ApplicationCall.queryParam(name: String): String =
        request.queryParameters[name] ?: throw ExceptionForCode("Missing query parameter '$name'", "BRM_PARAM_MISSING")

    @SuppressWarnings("UNCHECKED_CAST")
    protected fun <E> ApplicationCall.queryParam(name: String, default: E): E =
        (request.queryParameters[name] ?: default) as E

    protected fun ApplicationCall.idParam(): String {
        val id = request.queryParameters["id"]
        if (id == null) {
            throw ExceptionForCode("Invalid ID parameter", "BRM_PARAMID_INVALID")
        }
        if (id.length != 24) {
            throw ExceptionForCode("Invalid ID parameter length: ${id.length} must be 24", "BRM_PARAMID_LENGTH")
        }
        return id
    }

    // Функция преобразования JsonElement в нативные типы
    private fun jsonElementToNative(element: kotlinx.serialization.json.JsonElement): Any? {
        return when (element) {
            is kotlinx.serialization.json.JsonNull -> null
            is kotlinx.serialization.json.JsonPrimitive -> {
                when {
                    element.isString -> element.content
                    element.content == "true" || element.content == "false" -> element.content.toBoolean()
                    element.content.contains(".") -> element.content.toDoubleOrNull()
                    else -> {
                        element.content.toIntOrNull()
                            ?: element.content.toLongOrNull()
                            ?: element.content
                    }
                }
            }
            is kotlinx.serialization.json.JsonArray -> {
                element.map { jsonElementToNative(it) }
            }
            is kotlinx.serialization.json.JsonObject -> {
                element.mapValues { jsonElementToNative(it.value) }
            }
        }
    }

    protected suspend fun <T> ApplicationCall.respond(
        serializer: KSerializer<T>,
        value: T
    ) {
        val text = AppJson.encodeToString(serializer, value)
        respondText(text, ContentType.Application.Json, HttpStatusCode.OK)
    }
}

@Serializable
data class ApiMongoResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val code: String? = null,
    val errors: Map<String, String>? = null
) {
    companion object {
        fun <T> ok(data: T?, message: String? = null) =
            ApiMongoResponse(success = true, data = data, code = "200")
    }
}
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
 * Абстрактный базовый роут, предоставляющий стандартный CRUD-эндпоинты для REST API.
 *
 * **Основная идея:** Автоматическая генерация типовых REST-эндпоинтов для любой сущности
 * с минимальным количеством кода в наследниках.
 *
 * **Ключевые особенности:**
 * - Решает проблему type erasure при сериализации generic-типов через предвычисленные сериализаторы
 * - Предоставляет полноценный CRUD с поддержкой оптимистичной блокировки
 * - Поддерживает пагинацию и получение количества записей
 * - Предоставляет хуки (`additionalRoutes`) для расширения функциональности
 * - Автоматически обрабатывает параметры пути и query-параметры
 * - Унифицированный формат ответа через `ApiResponse`
 *
 * **Проблема, которую решает:**
 * ```kotlin
 * // Проблема: Ktor не может сериализовать ApiResponse<User> из-за type erasure
 * call.respond(ApiResponse.ok(user)) // Ошибка: нет сериализатора для ApiResponse<User>
 *
 * // Решение: предвычисленные сериализаторы создаются один раз в конструкторе
 * private val singleResponseSerializer: KSerializer<ApiResponse<E>> = ...
 * call.respond(HttpStatusCode.OK, singleResponseSerializer, ApiResponse.ok(user))
 * ```
 *
 * **Требования к наследникам:**
 * - Передать `entitySerializer` — сериализатор сущности (генерируется плагином kotlinx.serialization)
 * - Опционально: переопределить `additionalRoutes` для добавления кастомных эндпоинтов
 *
 * @param E Тип сущности (наследник `BaseEntity`)
 * @param T Тип таблицы (наследник `BaseTable`)
 * @property service Базовый сервис для бизнес-логики (наследник `BaseService`)
 * @property basePath Базовый путь для всех эндпоинтов (например, "/users")
 * @property entitySerializer Сериализатор Kotlinx для сущности (решает проблему type erasure)
 *
 * @author ORM Team
 * @since 1.0
 * @see BaseService
 * @see ApiResponse
 * @see BaseEntity
 *
 * @sample
 * ```kotlin
 * // Пример конкретного роута
 * class UserRoute(
 *     service: UserService,
 *     basePath: String = "/users"
 * ) : BaseRoute<User, UsersTable>(
 *     service = service,
 *     basePath = basePath,
 *     entitySerializer = User.serializer()
 * ) {
 *     override fun additionalRoutes(route: Route): Route {
 *         route.apply {
 *             get("/by-email/{email}") {
 *                 val email = call.param("email")
 *                 val user = service.findByEmail(email)
 *                 call.respondEntity(user)
 *             }
 *
 *             post("/bulk") {
 *                 val users = call.receive<List<User>>()
 *                 val created = service.createBulk(users)
 *                 call.respondEntityList(created, HttpStatusCode.Created)
 *             }
 *         }
 *         return route
 *     }
 * }
 *
 * // Регистрация в приложении
 * fun Application.main() {
 *     routing {
 *         UserRoute(userService).register(this)
 *     }
 * }
 * ```
 */
abstract class BaseRoute<E : BaseEntity, T : BaseTable>(
    protected val service: BaseService<E, T>,
    protected val basePath: String,
    entitySerializer: KSerializer<E>
) {

    // ========== Предвычисленные сериализаторы (создаются один раз) ==========

    /**
     * Сериализатор для единичного ответа `ApiResponse<E>`.
     * Создаётся один раз в конструкторе для оптимальной производительности.
     */
    private val singleResponseSerializer: KSerializer<ApiResponse<E>> =
        apiResponseSerializer(entitySerializer)

    /**
     * Сериализатор для ответа со списком сущностей `ApiResponse<List<E>>`.
     */
    private val listResponseSerializer: KSerializer<ApiResponse<List<E>>> =
        apiResponseListSerializer(entitySerializer)

    /**
     * Сериализатор для пагинированного ответа `ApiResponse<PagedResponse<E>>`.
     */
    private val pagedResponseSerializer: KSerializer<ApiResponse<PagedResponse<E>>> =
        apiResponsePagedSerializer(entitySerializer)

    // ==================== Регистрация ====================

    /**
     * Регистрирует все маршруты в переданном роутинге Ktor.
     *
     * **Порядок регистрации маршрутов:**
     * 1. Сначала регистрируются кастомные маршруты (через `additionalRoutes`)
     *    - Это важно, чтобы кастомные маршруты не перекрывались стандартными
     * 2. Затем все стандартные CRUD-маршруты
     *
     * **Стандартные маршруты:**
     * - `GET    /`           → получение всех записей
     * - `GET    /{id}`       → получение записи по ID
     * - `GET    /paged`      → пагинированный список
     * - `GET    /count`      → количество записей
     * - `POST   /`           → создание записи
     * - `PUT    /{id}`       → обновление записи
     * - `DELETE /{id}`       → удаление записи
     * - `DELETE /{id}/version/{version}` → удаление с проверкой версии
     *
     * @param routing Объект роутинга Ktor (обычно из `Application.routing {}`)
     *
     * @sample
     * ```kotlin
     * routing {
     *     UserRoute(userService).register(this)
     *     ProductRoute(productService).register(this)
     * }
     * ```
     */
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

    /**
     * GET / — получение всех записей.
     *
     * **Ответ:** `ApiResponse<List<E>>` с HTTP 200 OK
     *
     * @sample
     * ```http
     * GET /users
     * Response: {
     *   "success": true,
     *   "data": [{"id":1,"name":"John"}, {"id":2,"name":"Jane"}],
     *   "message": null
     * }
     * ```
     */
    private fun Route.getAllRoute() = get {
        val items = service.findAll()
        val response = ApiResponse.ok(items)
        call.respond(HttpStatusCode.OK, listResponseSerializer, response)
    }

    /**
     * GET /{id} — получение записи по идентификатору.
     *
     * **Параметры пути:**
     * - `id` — числовой идентификатор сущности
     *
     * **Ответ:** `ApiResponse<E>` с HTTP 200 OK
     *
     * **Ошибки:**
     * - `400 Bad Request` — ID отсутствует или не число
     * - `404 Not Found` — запись не найдена
     *
     * @sample
     * ```http
     * GET /users/123
     * Response: {
     *   "success": true,
     *   "data": {"id":123,"name":"John","email":"john@example.com"},
     *   "message": null
     * }
     * ```
     */
    private fun Route.getByIdRoute() = get("/{id}") {
        val entity = service.getById(call.longParam("id"))
        val response = ApiResponse.ok(entity)
        call.respond(HttpStatusCode.OK, singleResponseSerializer, response)
    }

    /**
     * POST / — создание новой записи.
     *
     * **Тело запроса:** JSON-объект с данными для создания
     * (обязательные поля должны присутствовать)
     *
     * **Ответ:** `ApiResponse<E>` с HTTP 201 Created
     *
     * **Ошибки:**
     * - `400 Bad Request` — отсутствуют обязательные поля или неверный формат
     *
     * @sample
     * ```http
     * POST /users
     * Body: {"name":"John Doe","email":"john@example.com"}
     * Response: {
     *   "success": true,
     *   "data": {"id":1,"name":"John Doe","email":"john@example.com","version":1},
     *   "message": null
     * }
     * ```
     */
    private fun Route.createRoute() = post {
        val json = call.receive<JsonObject>()
        val created = service.create(json)
        val response = ApiResponse.created(created)
        call.respond(HttpStatusCode.Created, singleResponseSerializer, response)
    }

    /**
     * PUT /{id} — полное или частичное обновление записи.
     *
     * **Параметры пути:**
     * - `id` — идентификатор обновляемой записи
     *
     * **Тело запроса:** JSON-объект с обновляемыми полями
     * (обязательно должен содержать поле `version` для оптимистичной блокировки)
     *
     * **Ответ:** `ApiResponse<E>` с HTTP 200 OK
     *
     * **Ошибки:**
     * - `400 Bad Request` — отсутствует `version` или неверный формат
     * - `404 Not Found` — запись не найдена
     * - `409 Conflict` — конфликт версий (оптимистичная блокировка)
     *
     * @sample
     * ```http
     * PUT /users/1
     * Body: {"version":1, "email":"newemail@example.com"}
     * Response: {
     *   "success": true,
     *   "data": {"id":1,"name":"John","email":"newemail@example.com","version":2},
     *   "message": "Updated"
     * }
     * ```
     */
    private fun Route.updateRoute() = put("/{id}") {
        val id = call.longParam("id")
        val json = call.receive<JsonObject>()
        val updated = service.update(id, json)
        val response = ApiResponse.ok(updated, "Updated")
        call.respond(HttpStatusCode.OK, singleResponseSerializer, response)
    }

    /**
     * DELETE /{id} — удаление записи без проверки версии.
     *
     * **Параметры пути:**
     * - `id` — идентификатор удаляемой записи
     *
     * **Ответ:** `ApiResponse<Unit>` с HTTP 200 OK
     *
     * **Ошибки:**
     * - `400 Bad Request` — ID отсутствует или не число
     * - (удаление несуществующей записи возвращает успех с false)
     *
     * @sample
     * ```http
     * DELETE /users/123
     * Response: {
     *   "success": true,
     *   "data": null,
     *   "message": "Deleted"
     * }
     * ```
     */
    private fun Route.deleteRoute() = delete("/{id}") {
        service.delete(call.longParam("id"))
        val response = ApiResponse.message("Deleted")
        call.respond(HttpStatusCode.OK, apiResponseUnitSerializer, response)
    }

    /**
     * DELETE /{id}/version/{version} — удаление записи с проверкой версии.
     *
     * **Параметры пути:**
     * - `id` — идентификатор удаляемой записи
     * - `version` — ожидаемая версия записи (для оптимистичной блокировки)
     *
     * **Ответ:** `ApiResponse<Unit>` с HTTP 200 OK
     *
     * **Ошибки:**
     * - `400 Bad Request` — ID или version отсутствуют или не числа
     * - `404 Not Found` — запись не найдена
     * - `409 Conflict` — версия не совпадает (конкурентное изменение)
     *
     * **Когда использовать:** Для предотвращения случайного удаления
     * устаревших версий данных.
     *
     * @sample
     * ```http
     * DELETE /users/123/version/2
     * Response: {
     *   "success": true,
     *   "data": null,
     *   "message": "Deleted"
     * }
     * ```
     */
    private fun Route.deleteWithVersionRoute() = delete("/{id}/version/{version}") {
        val id = call.longParam("id")
        val version = call.longParam("version")
        service.deleteWithVersion(id, version)
        val response = ApiResponse.message("Deleted")
        call.respond(HttpStatusCode.OK, apiResponseUnitSerializer, response)
    }

    /**
     * GET /paged — пагинированный список записей.
     *
     * **Query-параметры:**
     * - `page` — номер страницы (по умолчанию 0, первая страница)
     * - `size` — размер страницы (по умолчанию 20, максимум рекомендуется 100)
     *
     * **Ответ:** `ApiResponse<PagedResponse<E>>` с HTTP 200 OK
     *
     * **Структура `PagedResponse`:**
     * ```json
     * {
     *   "items": [...],
     *   "page": 0,
     *   "size": 20,
     *   "total": 100
     * }
     * ```
     *
     * @sample
     * ```http
     * GET /users/paged?page=2&size=50
     * Response: {
     *   "success": true,
     *   "data": {
     *     "items": [{"id":101,...}, ...],
     *     "page": 2,
     *     "size": 50,
     *     "total": 1000
     *   },
     *   "message": null
     * }
     * ```
     */
    private fun Route.pagedRoute() = get("/paged") {
        val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
        val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 20
        val paged = service.findPaged(page, size)
        val response = ApiResponse.ok(paged)
        call.respond(HttpStatusCode.OK, pagedResponseSerializer, response)
    }

    /**
     * GET /count — получение общего количества записей.
     *
     * **Ответ:** `ApiResponse<Map<String, Long>>` с HTTP 200 OK
     *
     * **Формат ответа:**
     * ```json
     * {
     *   "success": true,
     *   "data": {"count": 1234},
     *   "message": null
     * }
     * ```
     *
     * **Применение:** Для пагинации, индикаторов загрузки, статистики.
     *
     * @sample
     * ```http
     * GET /users/count
     * Response: {
     *   "success": true,
     *   "data": {"count": 1234},
     *   "message": null
     * }
     * ```
     */
    private fun Route.countRoute() = get("/count") {
        val count = service.count()
        val response = ApiResponse.ok(mapOf("count" to count))
        call.respond(HttpStatusCode.OK, apiResponseMapSerializer, response)
    }

    // ==================== Hook ====================

    /**
     * Хук для добавления кастомных маршрутов.
     *
     * **Переопределите этот метод в наследнике** для добавления специфических эндпоинтов.
     *
     * **Важно:** Кастомные маршруты регистрируются **до** стандартных CRUD-маршрутов,
     * что позволяет избежать конфликтов (например, `/paged` не перехватывается как `/{id}`).
     *
     * **Типичные сценарии использования:**
     * - Поиск по полям: `GET /by-email/{email}`
     * - Кастомные действия: `POST /{id}/activate`
     * - Bulk-операции: `POST /bulk`, `DELETE /bulk`
     * - Экспорт данных: `GET /export/csv`
     * - Сложные фильтры: `GET /search?name=John&age=30`
     *
     * @param route Объект `Route` для регистрации подмаршрутов
     * @return Тот же объект `Route` (для chain-вызовов)
     *
     * @throws NotImplementedException По умолчанию, требует переопределения.
     *
     * @sample
     * ```kotlin
     * override fun additionalRoutes(route: Route): Route {
     *     route.apply {
     *         get("/search") {
     *             val name = call.queryParam("name")
     *             val results = service.searchByName(name)
     *             call.respondEntityList(results)
     *         }
     *
     *         post("/{id}/restore") {
     *             val id = call.longParam("id")
     *             val restored = service.restore(id)
     *             call.respondEntity(restored)
     *         }
     *     }
     *     return route
     * }
     * ```
     */
    protected open fun additionalRoutes(route: Route): Route {
        TODO("Implement additional routes or make open")
    }

    // ==================== Utilities ====================

    /**
     * Извлекает Long-параметр из пути запроса.
     *
     * **Использование:** Для параметров типа `/{id}`, `/{userId}/posts/{postId}`
     *
     * @param name Имя параметра пути (например, "id", "version")
     * @return Значение параметра как Long
     * @throws BadRequestException Если параметр отсутствует или не является числом
     *
     * @sample
     * ```kotlin
     * get("/users/{id}") {
     *     val userId = call.longParam("id")
     *     val user = service.findById(userId)
     * }
     * ```
     */
    protected fun ApplicationCall.longParam(name: String): Long =
        parameters[name]?.toLongOrNull()
            ?: throw BadRequestException("Invalid or missing '$name'")

    /**
     * Извлекает строковый query-параметр из URL.
     *
     * **Использование:** Для параметров типа `?name=John&age=30`
     *
     * @param name Имя query-параметра
     * @return Значение параметра
     * @throws BadRequestException Если параметр отсутствует
     *
     * @sample
     * ```kotlin
     * get("/users/search") {
     *     val name = call.queryParam("name")
     *     val users = service.searchByName(name)
     * }
     * ```
     */
    protected fun ApplicationCall.queryParam(name: String): String =
        request.queryParameters[name]
            ?: throw BadRequestException("Missing query parameter '$name'")

    /**
     * Хелпер для `additionalRoutes` — отправляет ответ с единичной сущностью.
     *
     * **Преимущества:**
     * - Автоматически использует правильный сериализатор
     * - Формирует `ApiResponse` с данными
     * - Удобство в кастомных маршрутах
     *
     * @param entity Сущность для отправки
     * @param status HTTP статус (по умолчанию 200 OK)
     *
     * @sample
     * ```kotlin
     * get("/by-email/{email}") {
     *     val user = service.findByEmail(call.param("email"))
     *     call.respondEntity(user)
     * }
     * ```
     */
    protected suspend fun ApplicationCall.respondEntity(entity: E, status: HttpStatusCode = HttpStatusCode.OK) {
        respond(status, singleResponseSerializer, ApiResponse.ok(entity))
    }

    /**
     * Хелпер для `additionalRoutes` — отправляет ответ со списком сущностей.
     *
     * @param list Список сущностей для отправки
     * @param status HTTP статус (по умолчанию 200 OK)
     *
     * @sample
     * ```kotlin
     * get("/active") {
     *     val activeUsers = service.findActive()
     *     call.respondEntityList(activeUsers)
     * }
     * ```
     */
    protected suspend fun ApplicationCall.respondEntityList(list: List<E>, status: HttpStatusCode = HttpStatusCode.OK) {
        respond(status, listResponseSerializer, ApiResponse.ok(list))
    }

    /**
     * Хелпер для отправки ответа с произвольным типом данных.
     *
     * **Когда использовать:** Когда нужно отправить не сущность и не список,
     * например, статистику, агрегированные данные или DTO.
     *
     * @param R Тип данных ответа
     * @param serializer Сериализатор для `ApiResponse<R>`
     * @param data Данные для упаковки в `ApiResponse`
     * @param message Опциональное сообщение
     * @param status HTTP статус (по умолчанию 200 OK)
     *
     * @sample
     * ```kotlin
     * get("/stats") {
     *     val stats = mapOf("total" to 100, "active" to 80)
     *     call.respondTyped(apiResponseMapSerializer, stats, "Statistics retrieved")
     * }
     * ```
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
 * Расширение для `ApplicationCall`, добавляющее возможность ответа с явным сериализатором.
 *
 * **Проблема:** Ktor из коробки имеет `call.respond(status, message)`,
 * но не предоставляет `call.respond(status, serializer, value)` для явной сериализации.
 *
 * **Решение:** Это расширение позволяет использовать кастомные сериализаторы
 * для сложных generic-типов, где вывод типа недостаточен.
 *
 * @param T Тип сериализуемого объекта
 * @param status HTTP статус ответа
 * @param serializer Явный сериализатор для типа T
 * @param value Значение для сериализации и отправки
 *
 * @sample
 * ```kotlin
 * // Без расширения (ошибка)
 * call.respond(HttpStatusCode.OK, ApiResponse.ok(user)) // ❌ Нет сериализатора
 *
 * // С расширением
 * call.respond(HttpStatusCode.OK, userSerializer, ApiResponse.ok(user)) // ✅
 * ```
 *
 * @see ApplicationCall.respond
 * @see KSerializer
 */
suspend fun <T> ApplicationCall.respond(
    status: HttpStatusCode,
    serializer: KSerializer<T>,
    value: T
) {
    val text = AppJson.encodeToString(serializer, value)
    respondText(text, ContentType.Application.Json, status)
}
package base.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Универсальная обёртка для всех HTTP-ответов API.
 *
 * Обеспечивает единообразный формат ответа для всех эндпоинтов приложения,
 * что упрощает обработку на стороне клиента — клиенту достаточно проверить
 * поле [success], чтобы определить, был ли запрос успешным.ы
 *
 * ## Формат успешного ответа
 *
 * ```json
 * {
 *   "success": true,
 *   "data": { "id": 1, "name": "Alice", "version": 1 },
 *   "message": "Created",
 *   "errors": null
 * }
 * ```
 *
 * ## Формат ошибки
 *
 * ```json
 * {
 *   "success": false,
 *   "data": null,
 *   "message": "User with id=42 not found",
 *   "errors": ["Field 'email' is required", "Field 'name' must not be blank"]
 * }
 * ```
 *
 * ## Почему generic, а не `Any`
 *
 * Параметр типа [T] позволяет `kotlinx.serialization` корректно
 * сериализовать [data] без потери информации о типе. При использовании `Any`
 * сериализатор не знал бы конкретную структуру данных и не смог бы
 * сгенерировать правильный JSON. Конкретный `KSerializer<T>` передаётся
 * через фабрики сериализаторов (см. ниже).
 *
 * @param T Тип данных, содержащихся в поле [data].
 *          Может быть любым `@Serializable` типом: сущность, список, [PagedResponse] и т.д.
 *
 * @property success Флаг успешности операции.
 *                   `true` — запрос обработан корректно, [data] содержит результат.
 *                   `false` — произошла ошибка, описание в [message] и/или [errors].
 * @property data    Полезная нагрузка ответа. Присутствует только при [success] = `true`.
 *                   `null` для ответов без тела (удаление, ошибки).
 * @property message Человекочитаемое сообщение. При успехе — необязательное пояснение
 *                   (например, `"Created"`, `"Updated"`). При ошибке — основное описание проблемы.
 * @property errors  Список детализированных ошибок. Используется, когда одна операция
 *                   может порождать несколько ошибок валидации одновременно.
 *                   `null` если ошибок нет или достаточно одного [message].
 *
 * @see PagedResponse Обёртка для постраничной выдачи, используется как `ApiResponse<PagedResponse<E>>`
 */
@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
    val errors: List<String>? = null
) {
    companion object {

        /**
         * Формирует успешный ответ с данными.
         *
         * Используется для GET (чтение), PUT (обновление) и любых операций,
         * возвращающих результат клиенту.
         *
         * ## Пример
         *
         * ```kotlin
         * // В Route:
         * val user = service.getById(id)
         * val response = ApiResponse.ok(user)
         * // → { "success": true, "data": { ... }, "message": null }
         *
         * val updated = service.update(id, json)
         * val response = ApiResponse.ok(updated, "Updated")
         * // → { "success": true, "data": { ... }, "message": "Updated" }
         * ```
         *
         * @param T       Тип данных. Выводится автоматически из аргумента [data].
         * @param data    Объект для сериализации в поле `data` ответа.
         * @param message Необязательное пояснение (по умолчанию `null`).
         * @return [ApiResponse] с `success = true` и переданными данными.
         */
        fun <T> ok(data: T?, message: String? = null) =
            ApiResponse(success = true, data = data, message = message)

        /**
         * Формирует успешный ответ для операции создания.
         *
         * Отличается от [ok] только фиксированным сообщением `"Created"`.
         * Используется в паре с HTTP-статусом `201 Created`.
         *
         * ## Пример
         *
         * ```kotlin
         * val created = service.create(json)
         * call.respond(HttpStatusCode.Created, serializer, ApiResponse.created(created))
         * ```
         *
         * @param T    Тип созданной сущности.
         * @param data Созданная сущность (как правило, уже с присвоенным `id` и `version = 1`).
         * @return [ApiResponse] с `success = true`, данными и сообщением `"Created"`.
         */
        fun <T> created(data: T) =
            ApiResponse(success = true, data = data, message = "Created")

        /**
         * Формирует успешный ответ без данных — только текстовое сообщение.
         *
         * Используется для операций, не возвращающих тело:
         * удаление, деактивация, подтверждение действия.
         *
         * ## Пример
         *
         * ```kotlin
         * service.delete(id)
         * val response = ApiResponse.message("Deleted")
         * // → { "success": true, "data": null, "message": "Deleted" }
         * ```
         *
         * Тип данных явно указан как [Unit], поскольку поле `data` всегда `null`.
         * Для сериализации используется [apiResponseUnitSerializer].
         *
         * @param text Текст сообщения для клиента.
         * @return [ApiResponse]<[Unit]> с `success = true` и пустым `data`.
         */
        fun message(text: String) =
            ApiResponse<Unit>(success = true, message = text)

        /**
         * Формирует ответ об ошибке.
         *
         * Используется в StatusPages и в валидаторах для возврата
         * описания проблемы клиенту.
         *
         * ## Пример
         *
         * ```kotlin
         * // Одна ошибка:
         * ApiResponse.error("User with id=42 not found")
         * // → { "success": false, "message": "User with id=42 not found" }
         *
         * // Несколько ошибок валидации:
         * ApiResponse.error(
         *     message = "Validation failed",
         *     errors = listOf("'name' is required", "'email' format is invalid")
         * )
         * // → { "success": false, "message": "Validation failed", "errors": [...] }
         * ```
         *
         * @param message Основное описание ошибки.
         * @param errors  Опциональный список детализированных ошибок
         *                (полезно при множественной валидации).
         * @return [ApiResponse]<[Unit]> с `success = false`.
         */
        fun error(message: String, errors: List<String>? = null) =
            ApiResponse<Unit>(success = false, message = message, errors = errors)
    }
}

/**
 * Обёртка для постраничной выдачи сущностей.
 *
 * Используется как `ApiResponse<PagedResponse<E>>`, где `E` — тип сущности.
 * Содержит как сами данные текущей страницы, так и метаинформацию
 * для навигации по страницам на стороне клиента.
 *
 * ## Формат ответа
 *
 * ```json
 * {
 *   "success": true,
 *   "data": {
 *     "items": [ { "id": 1, ... }, { "id": 2, ... } ],
 *     "page": 0,
 *     "pageSize": 20,
 *     "totalItems": 157,
 *     "totalPages": 8
 *   }
 * }
 * ```
 *
 * ## Пример создания
 *
 * ```kotlin
 * // В BaseService.findPaged():
 * val items = repository.findPaged(page, pageSize)
 * val total = repository.count()
 * val pages = ((total + pageSize - 1) / pageSize).toInt()
 * return PagedResponse(items, page, pageSize, total, pages)
 * ```
 *
 * @param T Тип элементов списка (сущность).
 *
 * @property items      Список сущностей на текущей странице.
 *                      Может быть пустым, если страница за пределами данных.
 * @property page       Номер текущей страницы (нумерация с 0).
 *                      Передаётся клиентом как query-параметр `?page=0`.
 * @property pageSize   Размер страницы (количество элементов на странице).
 *                      Передаётся клиентом как query-параметр `?size=20`.
 * @property totalItems Общее количество записей в базе (без пагинации).
 *                      Позволяет клиенту показать "Найдено 157 записей".
 * @property totalPages Общее количество страниц.
 *                      Вычисляется как `ceil(totalItems / pageSize)`.
 *                      Позволяет клиенту отрисовать пагинатор.
 */
@Serializable
data class PagedResponse<T>(
    val items: List<T>,
    val page: Int,
    val pageSize: Int,
    val totalItems: Long,
    val totalPages: Int
)

// =========================================================================================
//  Фабрики сериализаторов
//
//  Проблема:
//    Ktor и kotlinx.serialization работают с reified-типами.
//    Когда BaseRoute<E> вызывает call.respond(ApiResponse.ok(entity)),
//    тип E стёрт (type erasure), и сериализатор не может быть найден автоматически.
//
//  Решение:
//    Каждый конкретный Route передаёт KSerializer<E> (например, User.serializer())
//    в конструктор BaseRoute. Из этого сериализатора строятся все необходимые
//    составные сериализаторы через фабричные функции ниже.
//
//  Жизненный цикл:
//    Сериализаторы создаются ОДИН РАЗ при инстанцировании Route (в конструкторе BaseRoute)
//    и переиспользуются для всех запросов — никакого оверхеда в рантайме.
// =========================================================================================

/**
 * Строит сериализатор для `ApiResponse<T>` — ответ с единичной сущностью.
 *
 * ## Использование в BaseRoute
 *
 * ```kotlin
 * // В конструкторе BaseRoute:
 * private val singleResponseSerializer = apiResponseSerializer(entitySerializer)
 *
 * // В обработчике:
 * val user = service.getById(id)
 * call.respond(status, singleResponseSerializer, ApiResponse.ok(user))
 * ```
 *
 * ## Что происходит внутри
 *
 * `ApiResponse.serializer(dataSerializer)` — вызывает автосгенерированный
 * `kotlinx.serialization` метод, который принимает сериализатор параметра типа `T`
 * и возвращает полный сериализатор для `ApiResponse<T>`.
 *
 * @param T              Тип данных в поле `data` (например, `User`, `Post`).
 * @param dataSerializer Сериализатор для типа [T].
 *                       Обычно получается через `User.serializer()`, `Post.serializer()`.
 * @return Полный сериализатор для `ApiResponse<T>`.
 */
fun <T> apiResponseSerializer(dataSerializer: KSerializer<T>): KSerializer<ApiResponse<T>> =
    ApiResponse.serializer(dataSerializer)

/**
 * Строит сериализатор для `ApiResponse<List<T>>` — ответ со списком сущностей.
 *
 * Используется для эндпоинтов, возвращающих коллекции:
 * `GET /api/users`, `GET /api/users/active`, `GET /api/posts/published`.
 *
 * ## Использование в BaseRoute
 *
 * ```kotlin
 * // В конструкторе BaseRoute:
 * private val listResponseSerializer = apiResponseListSerializer(entitySerializer)
 *
 * // В обработчике:
 * val users = service.findAll()
 * call.respond(status, listResponseSerializer, ApiResponse.ok(users))
 * ```
 *
 * ## Композиция сериализаторов
 *
 * Внутри происходит вложенная композиция:
 * ```
 * ApiResponse.serializer(          // внешний: ApiResponse<...>
 *     ListSerializer(              // средний: List<...>
 *         itemSerializer           // внутренний: T (например, User)
 *     )
 * )
 * ```
 *
 * @param T              Тип элемента списка (например, `User`).
 * @param itemSerializer Сериализатор для одного элемента типа [T].
 * @return Полный сериализатор для `ApiResponse<List<T>>`.
 */
fun <T> apiResponseListSerializer(itemSerializer: KSerializer<T>): KSerializer<ApiResponse<List<T>>> =
    ApiResponse.serializer(ListSerializer(itemSerializer))

/**
 * Строит сериализатор для `ApiResponse<PagedResponse<T>>` — постраничный ответ.
 *
 * Используется для эндпоинта `GET /api/users/paged?page=0&size=20`.
 *
 * ## Использование в BaseRoute
 *
 * ```kotlin
 * // В конструкторе BaseRoute:
 * private val pagedResponseSerializer = apiResponsePagedSerializer(entitySerializer)
 *
 * // В обработчике:
 * val paged = service.findPaged(page, size)
 * call.respond(status, pagedResponseSerializer, ApiResponse.ok(paged))
 * ```
 *
 * ## Композиция сериализаторов
 *
 * ```
 * ApiResponse.serializer(              // ApiResponse<...>
 *     PagedResponse.serializer(        // PagedResponse<...>
 *         itemSerializer               // T (например, User)
 *     )
 * )
 * ```
 *
 * Результат: сериализатор умеет корректно обработать вложенную структуру
 * `ApiResponse → PagedResponse → List<T> → T`.
 *
 * @param T              Тип сущности внутри постраничного списка.
 * @param itemSerializer Сериализатор для одного элемента типа [T].
 * @return Полный сериализатор для `ApiResponse<PagedResponse<T>>`.
 */
fun <T> apiResponsePagedSerializer(itemSerializer: KSerializer<T>): KSerializer<ApiResponse<PagedResponse<T>>> =
    ApiResponse.serializer(PagedResponse.serializer(itemSerializer))

/**
 * Предвычисленный сериализатор для `ApiResponse<Unit>`.
 *
 * Используется для ответов без полезной нагрузки — когда поле `data`
 * отсутствует или равно `null`:
 * - Успешное удаление: `ApiResponse.message("Deleted")`
 * - Ответы об ошибках: `ApiResponse.error("Not found")`
 *
 * ## Почему `Unit`, а не `Nothing`
 *
 * `Nothing` не имеет экземпляров и не может быть сериализован.
 * `Unit` — корректный тип с единственным значением, и `kotlinx.serialization`
 * имеет встроенный `Unit.serializer()`, который сериализует его как `{}`.
 * При `data = null` поле просто становится `null` в JSON.
 *
 * ## Пример
 *
 * ```kotlin
 * service.delete(id)
 * val response = ApiResponse.message("Deleted")
 * call.respond(HttpStatusCode.OK, apiResponseUnitSerializer, response)
 * // → { "success": true, "data": null, "message": "Deleted" }
 * ```
 *
 * Объявлен как `val` (а не `fun`), потому что параметров типа нет —
 * сериализатор создаётся один раз при загрузке класса.
 */
val apiResponseUnitSerializer: KSerializer<ApiResponse<Unit>> =
    ApiResponse.serializer(Unit.serializer())

/**
 * Предвычисленный сериализатор для `ApiResponse<Map<String, Long>>`.
 *
 * Используется для эндпоинтов, возвращающих числовую статистику:
 * - `GET /api/users/count` → `{ "count": 42 }`
 * - `GET /api/posts/count-by-author/1` → `{ "authorId": 1, "count": 7 }`
 *
 * ## Почему `Map<String, Long>`, а не специальный data class
 *
 * Для простых пар "ключ-число" создавать отдельный `@Serializable` класс
 * избыточно. `Map` достаточно гибок и не требует дополнительных моделей.
 * При необходимости можно добавить аналогичные сериализаторы для других
 * типов значений (`Map<String, Any>` и т.д.).
 *
 * ## Пример
 *
 * ```kotlin
 * val count = service.count()
 * val response = ApiResponse.ok(mapOf("count" to count))
 * call.respond(HttpStatusCode.OK, apiResponseMapSerializer, response)
 * // → { "success": true, "data": { "count": 42 } }
 * ```
 *
 * ## Композиция
 *
 * ```
 * ApiResponse.serializer(                  // ApiResponse<...>
 *     MapSerializer(                       // Map<...>
 *         String.serializer(),             // ключ: String
 *         Long.serializer()                // значение: Long
 *     )
 * )
 * ```
 *
 * Объявлен как `val` — типы фиксированы, создаётся однократно.
 */
val apiResponseMapSerializer: KSerializer<ApiResponse<Map<String, Long>>> =
    ApiResponse.serializer(MapSerializer(String.serializer(), Long.serializer()))
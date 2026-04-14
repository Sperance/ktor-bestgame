package base.exception

/**
 * Базовый sealed-класс для всех бизнес-исключений приложения.
 *
 * Использование `sealed class` гарантирует, что все возможные типы
 * бизнес-ошибок известны на этапе компиляции, что позволяет:
 * - обрабатывать их исчерпывающе в `when`-выражениях
 * - централизованно маппить на HTTP-коды в Ktor StatusPages
 * - предотвращать создание произвольных подтипов вне этого файла
 *
 * Наследуется от [RuntimeException], чтобы можно было бросать
 * без объявления в сигнатуре (`throws`) — Kotlin не имеет checked exceptions,
 * но это важно для совместимости с Java-кодом и middleware.
 *
 * ## Пример обработки в StatusPages
 *
 * ```kotlin
 * install(StatusPages) {
 *     exception<AppException> { call, cause ->
 *         val status = HttpStatusCode.fromValue(cause.httpCode)
 *         call.respond(status, ApiResponse.error(cause.message))
 *     }
 * }
 * ```
 *
 * ## Пример exhaustive when
 *
 * ```kotlin
 * fun handleError(ex: AppException): String = when (ex) {
 *     is NotFoundException       -> "Не найдено: ${ex.message}"
 *     is BadRequestException     -> "Невалидный запрос: ${ex.message}"
 *     is ConflictException       -> "Конфликт: ${ex.message}"
 *     is OptimisticLockException -> "Конкурентное изменение: ${ex.message}"
 *     // Компилятор гарантирует, что все ветки покрыты
 * }
 * ```
 *
 * @property message Человекочитаемое описание ошибки, которое будет
 *                   отправлено клиенту в теле JSON-ответа.
 * @property httpCode HTTP-статус-код, соответствующий данному типу ошибки.
 *                    Используется в StatusPages для формирования ответа.
 *
 * @see NotFoundException          Ресурс не найден (404)
 * @see BadRequestException        Невалидный запрос клиента (400)
 * @see ConflictException          Конфликт состояния ресурса (409)
 * @see OptimisticLockException    Конфликт версий при конкурентном обновлении (409)
 * @see UnauthorizedException      Неудачная аутентификация (401)
 */
sealed class AppException(
    override val message: String,
    val httpCode: Int
) : RuntimeException(message)

/**
 * Запрашиваемый ресурс не найден.
 *
 * Бросается, когда сущность с указанным идентификатором
 * не существует в базе данных. Маппится на HTTP 404 Not Found.
 *
 * ## Типичные сценарии
 *
 * - Запрос сущности по несуществующему ID
 * - Обращение к связанной сущности (например, автор поста), которая была удалена
 * - Поиск по уникальному полю (email, slug), не давший результатов
 *
 * ## Пример использования
 *
 * ```kotlin
 * fun getById(id: Long): User =
 *     repository.findById(id)
 *         ?: throw NotFoundException("User with id=$id not found")
 * ```
 *
 * @param message Описание того, что именно не было найдено.
 *                Рекомендуемый формат: `"EntityName with field=value not found"`.
 */
class NotFoundException(message: String) : AppException(message, 404)

/**
 * Запрос клиента невалиден.
 *
 * Бросается при нарушении бизнес-правил валидации входных данных.
 * Маппится на HTTP 400 Bad Request.
 *
 * ## Типичные сценарии
 *
 * - Отсутствие обязательных полей в JSON-теле запроса
 * - Невалидный формат данных (email без `@`, отрицательный возраст)
 * - Невалидные path- или query-параметры (`id=abc` вместо числа)
 * - Нарушение бизнес-ограничений на входные данные
 *
 * ## Пример использования
 *
 * ```kotlin
 * fun validateCreate(json: JsonObject) {
 *     val email = json["email"]?.jsonPrimitive?.content
 *         ?: throw BadRequestException("Field 'email' is required")
 *
 *     if ("@" !in email) {
 *         throw BadRequestException("Invalid email format: '$email'")
 *     }
 * }
 * ```
 *
 * @param message Описание того, что именно не прошло валидацию.
 *                Сообщение отправляется клиенту, поэтому не должно
 *                содержать чувствительных данных (пароли, токены).
 */
class BadRequestException(message: String) : AppException(message, 400)

/**
 * Конфликт состояния ресурса.
 *
 * Бросается, когда запрос не может быть выполнен из-за текущего
 * состояния ресурса на сервере. Маппится на HTTP 409 Conflict.
 *
 * ## Отличие от [OptimisticLockException]
 *
 * - [ConflictException] — логический конфликт бизнес-правил
 *   (например, дублирование email)
 * - [OptimisticLockException] — конфликт версий при конкурентном
 *   обновлении одной и той же записи
 *
 * ## Типичные сценарии
 *
 * - Попытка создать пользователя с уже существующим email
 * - Попытка перевести заказ в статус, несовместимый с текущим
 * - Нарушение уникальных бизнес-ограничений
 *
 * ## Пример использования
 *
 * ```kotlin
 * fun validateCreate(json: JsonObject) {
 *     val email = json["email"]?.jsonPrimitive?.content ?: return
 *     repository.findByEmail(email)?.let {
 *         throw ConflictException("Email '$email' is already taken")
 *     }
 * }
 * ```
 *
 * @param message Описание конфликта. Рекомендуемый формат:
 *                `"Описание конфликта: 'значение'"`.
 */
class ConflictException(message: String) : AppException(message, 409)

/**
 * Конфликт версий при конкурентном обновлении (Optimistic Locking).
 *
 * Бросается, когда клиент пытается обновить или удалить ресурс,
 * но другая транзакция уже изменила его. Маппится на HTTP 409 Conflict.
 *
 * ## Механизм работы
 *
 * 1. Клиент A читает сущность: `{ id: 1, name: "X", version: 3 }`
 * 2. Клиент B обновляет ту же сущность → `version` становится `4`
 * 3. Клиент A отправляет `PUT { name: "Y", version: 3 }`
 * 4. Сервер выполняет: `UPDATE ... WHERE id=1 AND version=3` → 0 строк
 * 5. Сервер бросает [OptimisticLockException]
 * 6. Клиент A получает `409 Conflict` с инструкцией перечитать и повторить
 *
 * ## SQL, который генерируется в [base.repository.BaseRepository]
 *
 * ```sql
 * UPDATE users
 * SET name = 'Y', version = 4, updated_at = now()
 * WHERE id = 1 AND version = 3
 * -- Если 0 rows affected → OptimisticLockException
 * ```
 *
 * ## Рекомендуемая обработка на клиенте
 *
 * ```
 * 1. Получить 409 Conflict
 * 2. Перечитать ресурс (GET /api/users/1) → получить актуальную version
 * 3. Показать пользователю diff (опционально)
 * 4. Повторить обновление с новой version
 * ```
 *
 * ## Пример использования
 *
 * ```kotlin
 * // В BaseRepository.update():
 * val updatedRows = table.update({
 *     (table.id eq id) and (table.version eq expectedVersion)
 * }) { ... }
 *
 * if (updatedRows == 0) {
 *     if (!exists(id)) throw NotFoundException("User(id=$id) not found")
 *     throw OptimisticLockException("User", id, expectedVersion)
 * }
 * ```
 *
 * @param entityName  Имя сущности (например, `"User"`, `"Post"`).
 *                    Используется для формирования понятного сообщения об ошибке.
 * @param id          Идентификатор записи, которую пытались обновить.
 * @param expectedVersion Версия, которую клиент отправил в запросе.
 *                        Не совпала с текущей версией в БД, что и вызвало конфликт.
 */
class OptimisticLockException(
    entityName: String,
    id: Long,
    expectedVersion: Long
) : AppException(
    message = "$entityName(id=$id) was modified by another transaction. " +
            "Expected version=$expectedVersion. Please re-fetch and retry.",
    httpCode = 409
)

/**
 * Неудачная аутентификация.
 *
 * Бросается, когда клиент предоставил невалидные учётные данные
 * (неверный логин, пароль, токен). Маппится на HTTP 401 Unauthorized.
 *
 * ## Типичные сценарии
 *
 * - Неверный login или password при входе
 * - Истёкший или невалидный токен авторизации
 * - Попытка доступа без учётных данных
 *
 * ## Пример использования
 *
 * ```kotlin
 * fun authenticate(login: String, password: String): User {
 *     val user = repo.findByLogin(login)
 *         ?: throw UnauthorizedException("Invalid login or password")
 *     if (!verifyPassword(password, user.salt, user.hash)) {
 *         throw UnauthorizedException("Invalid login or password")
 *     }
 *     return user
 * }
 * ```
 *
 * @param message Описание причины отказа. **Не должно** раскрывать,
 *                что именно неверно (логин или пароль), чтобы
 *                не помогать при подборе учётных данных.
 */
class UnauthorizedException(message: String) : AppException(message, 401)
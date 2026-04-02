package base.service

import base.exception.NotFoundException
import base.model.BaseEntity
import base.model.PagedResponse
import base.repository.BaseRepository
import base.table.BaseTable
import kotlinx.serialization.json.JsonObject

/**
 * Абстрактный базовый сервис, реализующий бизнес-логику поверх репозитория.
 *
 * **Основная идея:** Концентрация бизнес-правил и валидации в одном месте,
 * отделённом от HTTP-слоя (роутов) и слоя доступа к данным (репозиториев).
 *
 * **Ключевые особенности:**
 * - Предоставляет стандартную CRUD-логику с поддержкой оптимистичной блокировки
 * - Реализует пагинацию с вычислением общего количества страниц
 * - Предоставляет хуки для валидации (`validateCreate`, `validateUpdate`)
 * - Унифицированная обработка ошибок (NotFoundException)
 * - Делегирует непосредственную работу с БД репозиторию
 *
 * **Архитектурное место в приложении:**
 *
 * ```
 * [HTTP Route] → [Service] → [Repository] → [Database]
 *      ↑             ↑             ↑
 *   HTTP layer   Business     Data access
 *                logic         layer
 * ```
 *
 * **Почему сервис, если есть репозиторий?**
 * - Репозиторий отвечает только за доступ к данным (CRUD)
 * - Сервис содержит бизнес-правила, валидацию, транзакционную логику
 * - Сервис может комбинировать несколько репозиториев
 *
 * @param E Тип сущности (наследник `BaseEntity`)
 * @param T Тип таблицы (наследник `BaseTable`)
 * @property repository Базовый репозиторий для доступа к данным
 *
 * @author ORM Team
 * @since 1.0
 * @see BaseRepository
 * @see BaseRoute
 * @see PagedResponse
 *
 * @sample
 * ```kotlin
 * // Пример конкретного сервиса
 * class UserService(
 *     repository: UserRepository
 * ) : BaseService<User, UsersTable>(repository) {
 *
 *     // Кастомная бизнес-логика
 *     fun findByEmail(email: String): User? = repository.findByEmail(email)
 *
 *     fun activateUser(id: Long): User {
 *         val user = getById(id)
 *         if (user.isActive) {
 *             throw ConflictException("User already active")
 *         }
 *         return repository.update(id, jsonOf("isActive" to true, "version" to user.version))
 *     }
 *
 *     // Валидация при создании
 *     override fun validateCreate(json: JsonObject) {
 *         val email = json["email"]?.jsonPrimitive?.content
 *             ?: throw BadRequestException("Email is required")
 *
 *         if (findByEmail(email) != null) {
 *             throw ConflictException("Email already exists")
 *         }
 *
 *         val age = json["age"]?.jsonPrimitive?.intOrNull
 *         if (age != null && age < 18) {
 *             throw BadRequestException("User must be at least 18 years old")
 *         }
 *     }
 *
 *     // Валидация при обновлении
 *     override fun validateUpdate(id: Long, json: JsonObject) {
 *         val email = json["email"]?.jsonPrimitive?.content
 *         if (email != null) {
 *             val existing = findByEmail(email)
 *             if (existing != null && existing.id != id) {
 *                 throw ConflictException("Email already taken by another user")
 *             }
 *         }
 *     }
 *
 *     override fun entityName(): String = "User"
 * }
 *
 * // Использование в роуте
 * class UserRoute(service: UserService) : BaseRoute<User, UsersTable>(
 *     service = service,
 *     basePath = "/users",
 *     entitySerializer = User.serializer()
 * )
 * ```
 */
abstract class BaseService<E : BaseEntity, T : BaseTable>(
    protected val repository: BaseRepository<E, T>
) {

    // ==================== READ Operations ====================

    /**
     * Возвращает все записи из таблицы.
     *
     * **Внимание:** При большом количестве записей (тысячи+) рекомендуется
     * использовать пагинацию через `findPaged()` вместо этого метода.
     *
     * @return Список всех сущностей (может быть пустым)
     *
     * @sample
     * ```kotlin
     * val allUsers = userService.findAll()
     * log.info("Total users: ${allUsers.size}")
     * ```
     */
    open fun findAll(): List<E> = repository.findAll()

    /**
     * Находит сущность по идентификатору.
     *
     * @param id Идентификатор сущности
     * @return Сущность или `null`, если не найдена
     *
     * @sample
     * ```kotlin
     * val user = userService.findById(1)
     * user?.let { processUser(it) } ?: log.warn("User not found")
     * ```
     */
    open fun findById(id: Long): E? = repository.findById(id)

    /**
     * Находит сущность по идентификатору или выбрасывает исключение.
     *
     * **Отличие от `findById`:** Вместо возврата `null` выбрасывает `NotFoundException`.
     * Удобно в ситуациях, когда отсутствие записи является ошибкой.
     *
     * @param id Идентификатор сущности
     * @return Сущность (гарантированно существующая)
     * @throws NotFoundException Если запись с указанным ID не существует
     *
     * @sample
     * ```kotlin
     * // В роуте или другом сервисе
     * val user = userService.getById(userId) // Безопасно, исключение при отсутствии
     * // Дальнейшая работа с user без проверки на null
     * ```
     */
    open fun getById(id: Long): E =
        findById(id) ?: throw NotFoundException("${entityName()} with id=$id not found")

    // ==================== CREATE Operations ====================

    /**
     * Создаёт новую запись на основе JSON-данных.
     *
     * **Процесс создания:**
     * 1. Вызывает `validateCreate(json)` для проверки бизнес-правил
     * 2. Делегирует создание репозиторию
     * 3. Возвращает созданную сущность (с заполненными id, version, createdAt, updatedAt)
     *
     * **Важные моменты:**
     * - Все обязательные поля должны присутствовать в JSON
     * - Поле `version` устанавливается автоматически (1)
     * - Поля `createdAt` и `updatedAt` заполняются текущим временем
     *
     * @param json JSON-объект с данными для создания
     * @return Созданная сущность
     * @throws BadRequestException При ошибках валидации
     *
     * @see validateCreate
     *
     * @sample
     * ```kotlin
     * val json = buildJsonObject {
     *     put("name", "John Doe")
     *     put("email", "john@example.com")
     * }
     * val newUser = userService.create(json)
     * println("Created user with id: ${newUser.id}")
     * ```
     */
    open fun create(json: JsonObject): E {
        validateCreate(json)
        return repository.create(json)
    }

    // ==================== UPDATE Operations ====================

    /**
     * Обновляет существующую запись с использованием оптимистичной блокировки.
     *
     * **Процесс обновления:**
     * 1. Вызывает `validateUpdate(id, json)` для проверки бизнес-правил
     * 2. Делегирует обновление репозиторию
     * 3. Репозиторий выполняет UPDATE с проверкой версии
     * 4. Возвращает обновлённую сущность (с инкрементированной version)
     *
     * **Требования к JSON:**
     * - Должен содержать поле `version` (текущая версия сущности)
     * - Остальные поля опциональны (partial update)
     *
     * **Обработка конфликтов:**
     * - Если версия не совпадает → `OptimisticLockException`
     * - Если запись не найдена → `NotFoundException`
     *
     * @param id Идентификатор обновляемой сущности
     * @param json JSON-объект с полями для обновления (обязательно поле `version`)
     * @return Обновлённая сущность
     * @throws NotFoundException Если запись не найдена
     * @throws OptimisticLockException При конфликте версий
     * @throws BadRequestException При ошибках валидации
     *
     * @see validateUpdate
     *
     * @sample
     * ```kotlin
     * // Клиент получил пользователя с version=1
     * val updateJson = buildJsonObject {
     *     put("version", 1)
     *     put("email", "newemail@example.com")
     * }
     * val updated = userService.update(1, updateJson)
     * // updated.version == 2
     * ```
     */
    open fun update(id: Long, json: JsonObject): E {
        validateUpdate(id, json)
        return repository.update(id, json)
    }

    // ==================== DELETE Operations ====================

    /**
     * Удаляет запись по идентификатору (без проверки версии).
     *
     * **Безопасность:** Перед удалением проверяет существование записи.
     * Если запись не существует — выбрасывает `NotFoundException`.
     *
     * **Когда использовать:** Когда не важна оптимистичная блокировка
     * или вы уверены в актуальности удаляемых данных.
     *
     * @param id Идентификатор удаляемой записи
     * @throws NotFoundException Если запись не существует
     *
     * @sample
     * ```kotlin
     * try {
     *     userService.delete(123)
     *     log.info("User deleted")
     * } catch (e: NotFoundException) {
     *     log.warn("User already deleted")
     * }
     * ```
     */
    open fun delete(id: Long) {
        if (!repository.exists(id)) {
            throw NotFoundException("${entityName()} with id=$id not found")
        }
        repository.delete(id)
    }

    /**
     * Удаляет запись с проверкой версии (оптимистичная блокировка).
     *
     * **Когда использовать:** Когда важно убедиться, что вы удаляете именно ту версию,
     * которую получили клиентом. Предотвращает случайное удаление после чужих изменений.
     *
     * @param id Идентификатор удаляемой записи
     * @param version Ожидаемая версия записи
     * @throws NotFoundException Если запись не существует
     * @throws OptimisticLockException Если версия не совпадает
     *
     * @sample
     * ```kotlin
     * // Клиент получил пользователя с version=2
     * userService.deleteWithVersion(123, 2) // Удалит только если version==2
     * ```
     */
    open fun deleteWithVersion(id: Long, version: Long) {
        repository.deleteWithVersion(id, version)
    }

    // ==================== Utility Operations ====================

    /**
     * Возвращает общее количество записей в таблице.
     *
     * @return Количество записей
     *
     * @sample
     * ```kotlin
     * val totalUsers = userService.count()
     * log.info("Total users in system: $totalUsers")
     * ```
     */
    open fun count(): Long = repository.count()

    /**
     * Проверяет существование записи с указанным ID.
     *
     * @param id Идентификатор для проверки
     * @return `true` если запись существует, иначе `false`
     *
     * @sample
     * ```kotlin
     * if (userService.exists(userId)) {
     *     // Выполняем операцию
     * }
     * ```
     */
    open fun exists(id: Long): Boolean = repository.exists(id)

    /**
     * Возвращает пагинированный список записей.
     *
     * **Отличие от `repository.findPaged`:** Этот метод также вычисляет
     * общее количество страниц на основе общего количества записей.
     *
     * **Формулы:**
     * - Общее количество страниц = ceil(total / pageSize)
     * - Если pageSize == 0, то pages = 0 (защита от деления на ноль)
     *
     * @param page Номер страницы (начиная с 0)
     * @param pageSize Количество записей на странице (по умолчанию 20)
     * @return Объект `PagedResponse` с элементами, метаданными пагинации
     *
     * @sample
     * ```kotlin
     * // Получаем вторую страницу пользователей (записи 21-40)
     * val page = userService.findPaged(page = 1, pageSize = 20)
     * println("Showing ${page.items.size} of ${page.total} users")
     * println("Page ${page.page + 1} of ${page.pages}")
     * ```
     */
    open fun findPaged(page: Int, pageSize: Int = 20): PagedResponse<E> {
        val items = repository.findPaged(page, pageSize)
        val total = repository.count()
        val pages = if (pageSize > 0) ((total + pageSize - 1) / pageSize).toInt() else 0
        return PagedResponse(items, page, pageSize, total, pages)
    }

    // ========== Hooks — переопределяются в наследниках ==========

    /**
     * Хук для валидации данных перед созданием сущности.
     *
     * **Переопределите этот метод** для добавления бизнес-правил при создании.
     *
     * **Типичные проверки:**
     * - Наличие обязательных полей (если не используются аннотации)
     * - Уникальность email/username
     * - Валидация форматов (email, телефон, дата)
     * - Бизнес-ограничения (возраст, лимиты, бюджет)
     * - Проверка ссылочной целостности (существуют ли связанные записи)
     *
     * **Важно:** Этот метод не должен изменять данные или выполнять побочные действия.
     * Только проверки и выбрасывание исключений при ошибках.
     *
     * @param json JSON-объект с данными для создания
     * @throws BadRequestException При нарушении бизнес-правил
     *
     * @sample
     * ```kotlin
     * override fun validateCreate(json: JsonObject) {
     *     // Проверка email
     *     val email = json["email"]?.jsonPrimitive?.content
     *         ?: throw BadRequestException("Email is required")
     *
     *     if (!EMAIL_REGEX.matches(email)) {
     *         throw BadRequestException("Invalid email format")
     *     }
     *
     *     // Проверка уникальности
     *     if (findByEmail(email) != null) {
     *         throw ConflictException("Email already exists")
     *     }
     *
     *     // Бизнес-правило
     *     val age = json["age"]?.jsonPrimitive?.intOrNull
     *     if (age != null && age < 18) {
     *         throw BadRequestException("User must be 18 or older")
     *     }
     * }
     * ```
     */
    protected open fun validateCreate(json: JsonObject) {}

    /**
     * Хук для валидации данных перед обновлением сущности.
     *
     * **Переопределите этот метод** для добавления бизнес-правил при обновлении.
     *
     * **Отличия от `validateCreate`:**
     * - Получает `id` обновляемой сущности
     * - Может учитывать существующее состояние при проверках
     * - Некоторые поля могут быть необязательными (partial update)
     *
     * **Типичные проверки:**
     * - Существование связанных записей
     * - Уникальность полей (исключая текущую запись)
     * - Бизнес-правила в зависимости от нового значения
     * - Права доступа (на уровне сервиса)
     *
     * @param id Идентификатор обновляемой сущности
     * @param json JSON-объект с полями для обновления
     * @throws BadRequestException При нарушении бизнес-правил
     *
     * @sample
     * ```kotlin
     * override fun validateUpdate(id: Long, json: JsonObject) {
     *     // Проверка email (если он меняется)
     *     val newEmail = json["email"]?.jsonPrimitive?.content
     *     if (newEmail != null) {
     *         val existing = findByEmail(newEmail)
     *         if (existing != null && existing.id != id) {
     *             throw ConflictException("Email already taken by another user")
     *         }
     *     }
     *
     *     // Бизнес-правило: нельзя понизить возраст админа
     *     val user = getById(id)
     *     if (user.role == "admin" && json["age"]?.jsonPrimitive?.intOrNull != null) {
     *         val newAge = json["age"]!!.jsonPrimitive.int
     *         if (newAge < user.age) {
     *             throw BadRequestException("Cannot decrease admin's age")
     *         }
     *     }
     * }
     * ```
     */
    protected open fun validateUpdate(id: Long, json: JsonObject) {}

    /**
     * Возвращает имя сущности для сообщений об ошибках и логирования.
     *
     * **Переопределите этот метод** для кастомизации текста ошибок.
     *
     * @return Имя сущности в человекочитаемом формате (по умолчанию "Entity")
     *
     * @sample
     * ```kotlin
     * override fun entityName(): String = "User"
     * // Ошибка будет: "User with id=123 not found"
     * ```
     */
    protected open fun entityName(): String = "Entity"
}
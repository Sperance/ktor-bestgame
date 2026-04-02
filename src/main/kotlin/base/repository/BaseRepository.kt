package base.repository

import base.exception.BadRequestException
import base.exception.NotFoundException
import base.exception.OptimisticLockException
import base.model.BaseEntity
import base.reflection.ReflectiveMapper
import base.table.BaseTable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import kotlin.reflect.KClass

/**
 * Абстрактный базовый репозиторий, предоставляющий стандартный CRUD-функционал с поддержкой оптимистичной блокировки.
 *
 * **Основная идея:** Унифицированный доступ к данным для всех сущностей проекта.
 * Репозиторий работает напрямую с сырым `JsonObject` от клиента, что исключает необходимость
 * создавать отдельные DTO-классы (CreateRequest, UpdateRequest) для каждой сущности.
 *
 * **Ключевые особенности:**
 * - Полная поддержка оптимистичной блокировки через поле `version`
 * - Автоматическая установка `createdAt` и `updatedAt` при создании/обновлении
 * - Сквозная работа с JSON от клиента до БД
 * - Рефлексивный маппинг через `ReflectiveMapper`
 * - Безопасная работа с транзакциями Exposed
 *
 * **Требования к сущностям:**
 * - Должны наследоваться от `BaseEntity` (содержит поля id, version, createdAt, updatedAt)
 * - Должны иметь primary-конструктор с параметрами, соответствующими колонкам таблицы
 *
 * **Требования к таблицам:**
 * - Должны наследоваться от `BaseTable` (содержит колонки id, version, createdAt, updatedAt)
 * - Должны определять связь с сущностью через generic-параметр
 *
 * @param E Тип сущности (наследник `BaseEntity`)
 * @param T Тип таблицы (наследник `BaseTable`)
 * @property table Экземпляр таблицы базы данных (например, `UsersTable`)
 * @property entityClass KClass сущности (например, `User::class`)
 *
 * @author ORM Team
 * @since 1.0
 * @see BaseEntity
 * @see BaseTable
 * @see ReflectiveMapper
 * @see OptimisticLockException
 */
abstract class BaseRepository<E : BaseEntity, T : BaseTable>(
    protected val table: T,
    protected val entityClass: KClass<E>
) {

    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * Имя сущности для логирования и сообщений об ошибках.
     * По умолчанию используется простое имя класса.
     * Может быть переопределено в наследниках для кастомизации.
     */
    protected open val entityName: String = entityClass.simpleName ?: "Entity"

    // ==================== Row → Entity ====================

    /**
     * Преобразует строку результата SQL-запроса в экземпляр сущности.
     *
     * **Зачем переопределять:** Если требуется специальная логика маппинга
     * (например, трансформация вложенных объектов или агрегатов).
     *
     * **Стандартное поведение:** Делегирует работу `ReflectiveMapper`.
     *
     * @param row Строка результата из Exposed
     * @return Созданный экземпляр сущности
     *
     * @sample
     * ```kotlin
     * override fun toEntity(row: ResultRow): User {
     *     val user = super.toEntity(row)
     *     user.roles = loadRoles(user.id)
     *     return user
     * }
     * ```
     */
    protected open fun toEntity(row: ResultRow): E =
        ReflectiveMapper.toEntity(entityClass, table, row)

    // ==================== READ ====================

    /**
     * Возвращает все записи из таблицы, отсортированные по id по возрастанию.
     *
     * **Производительность:** При большом количестве записей может быть проблематично.
     * Для production-сценариев рекомендуется использовать пагинацию (`findPaged`).
     *
     * @return Список всех сущностей (может быть пустым)
     *
     * @sample
     * ```kotlin
     * val allUsers = userRepository.findAll()
     * ```
     */
    open fun findAll(): List<E> = transaction {
        table.selectAll()
            .orderBy(table.id, SortOrder.ASC)
            .map(::toEntity)
    }

    /**
     * Находит сущность по её первичному ключу.
     *
     * @param id Идентификатор сущности (первичный ключ)
     * @return Сущность или `null`, если не найдена
     *
     * @sample
     * ```kotlin
     * val user = userRepository.findById(1) ?: throw NotFoundException("User not found")
     * ```
     */
    open fun findById(id: Long): E? = transaction {
        table.selectAll()
            .where { table.id eq id }
            .singleOrNull()
            ?.let(::toEntity)
    }

    /**
     * Возвращает страницу записей с поддержкой пагинации.
     *
     * **Важно:** Страницы нумеруются с 0 (ноль — первая страница).
     *
     * @param page Номер страницы (начиная с 0)
     * @param pageSize Количество записей на странице
     * @return Список сущностей на запрошенной странице
     *
     * @sample
     * ```kotlin
     * // Первые 20 записей
     * val firstPage = repository.findPaged(0, 20)
     *
     * // Вторая страница (записи 21-40)
     * val secondPage = repository.findPaged(1, 20)
     * ```
     */
    open fun findPaged(page: Int, pageSize: Int): List<E> = transaction {
        table.selectAll()
            .orderBy(table.id, SortOrder.ASC)
            .limit(pageSize)
            .offset(page.toLong() * pageSize)
            .map(::toEntity)
    }

    /**
     * Возвращает общее количество записей в таблице.
     *
     * @return Количество строк
     *
     * @sample
     * ```kotlin
     * val totalUsers = userRepository.count()
     * ```
     */
    open fun count(): Long = transaction { table.selectAll().count() }

    /**
     * Проверяет существование записи с указанным ID.
     *
     * @param id Идентификатор для проверки
     * @return `true` если запись существует, иначе `false`
     */
    open fun exists(id: Long): Boolean = transaction {
        table.selectAll().where { table.id eq id }.count() > 0
    }

    // ==================== CREATE ====================

    /**
     * Создаёт новую запись в БД на основе JSON-данных от клиента.
     *
     * **Процесс создания:**
     * 1. Логирует входящий JSON
     * 2. Заполняет INSERT-оператор через `ReflectiveMapper.insertFromJson`
     * 3. Автоматически устанавливает:
     *    - `version = 1` (начальная версия для оптимистичной блокировки)
     *    - `createdAt = текущее время`
     *    - `updatedAt = текущее время`
     * 4. Выполняет вставку и получает сгенерированный ID
     * 5. Загружает и возвращает созданную сущность
     *
     * **Обработка ошибок:**
     * - `BadRequestException` — если отсутствуют обязательные поля
     * - Другие исключения от `ReflectiveMapper` и Exposed
     *
     * @param json JSON-объект с данными для создания (должен содержать все required-поля)
     * @return Созданная сущность (с заполненными id, version, createdAt, updatedAt)
     *
     * @throws BadRequestException При валидационных ошибках
     *
     * @sample
     * ```kotlin
     * val json = JsonObject(mapOf(
     *     "name" to JsonPrimitive("John Doe"),
     *     "email" to JsonPrimitive("john@example.com")
     * ))
     * val newUser = userRepository.create(json)
     * println("Created user with id: ${newUser.id}")
     * ```
     */
    open fun create(json: JsonObject): E = transaction {
        log.debug("Creating {} from JSON: {}", entityName, json)

        val insertedId = table.insert { stmt ->
            ReflectiveMapper.insertFromJson(stmt, table, json, entityClass)
            stmt[table.version] = 1L
            stmt[table.createdAt] = LocalDateTime.now()
            stmt[table.updatedAt] = LocalDateTime.now()
        } get table.id

        findById(insertedId)!!
    }

    // ==================== UPDATE (Optimistic Locking) ====================

    /**
     * Обновляет существующую запись с использованием оптимистичной блокировки.
     *
     * **Алгоритм работы с оптимистичной блокировкой:**
     * 1. Извлекает поле `version` из JSON-объекта
     * 2. Формирует UPDATE-запрос с условием: `id = :id AND version = :expectedVersion`
     * 3. Выполняет обновление через `ReflectiveMapper.updateFromJson`
     * 4. Инкрементирует версию: `newVersion = oldVersion + 1`
     * 5. Обновляет поле `updatedAt`
     * 6. Если затронуто 0 строк — проверяет причину:
     *    - Запись не существует → `NotFoundException`
     *    - Версия не совпадает → `OptimisticLockException`
     *
     * **Важно:** Клиент **обязан** передавать текущую версию сущности в JSON.
     * Без этого обновление невозможно (выбрасывается `BadRequestException`).
     *
     * **Требования к JSON:**
     * - Поле `version` — обязательно (Long)
     * - Остальные поля — опциональны (partial update)
     *
     * @param id Идентификатор обновляемой сущности
     * @param json JSON-объект с полями для обновления (должен содержать `version`)
     * @return Обновлённая сущность (с новой версией и updatedAt)
     *
     * @throws BadRequestException Если отсутствует или некорректно поле `version`
     * @throws NotFoundException Если сущность с указанным id не существует
     * @throws OptimisticLockException Если версия не совпадает (конкурентное обновление)
     *
     * @sample
     * ```kotlin
     * // Клиент получил сущность с version=1, меняет email
     * val json = JsonObject(mapOf(
     *     "version" to JsonPrimitive(1),
     *     "email" to JsonPrimitive("new@example.com")
     * ))
     * val updatedUser = userRepository.update(1, json)
     * // Теперь version=2
     * ```
     */
    open fun update(id: Long, json: JsonObject): E {
        var expectedVersion: Long = -1

        val updatedRows = transaction {
            table.update({
                // Сначала парсим version из json внутри update-лямбды
                // Но нам нужен version до построения WHERE... Делаем в два шага:
                expectedVersion = extractVersion(json)
                (table.id eq id) and (table.version eq expectedVersion)
            }) { stmt ->
                ReflectiveMapper.updateFromJson(stmt, table, json, entityClass)
                stmt[table.version] = expectedVersion + 1
                stmt[table.updatedAt] = LocalDateTime.now()
            }
        }

        if (updatedRows == 0) {
            throwLockOrNotFound(id, expectedVersion)
        }

        log.debug("Updated $entityName(id=$id): version $expectedVersion → ${expectedVersion + 1}")
        return findById(id)!!
    }

    // ==================== DELETE ====================

    /**
     * Удаляет запись по идентификатору (без проверки версии).
     *
     * **Когда использовать:** Когда оптимистичная блокировка не требуется
     * или вы уверены, что запись не изменится во время удаления.
     *
     * **Безопасность:** Операция всегда успешна, даже если запись уже удалена
     * (просто вернёт `false`).
     *
     * @param id Идентификатор удаляемой записи
     * @return `true` если запись была удалена, `false` если не существовала
     *
     * @sample
     * ```kotlin
     * if (userRepository.delete(1)) {
     *     println("User deleted")
     * }
     * ```
     */
    open fun delete(id: Long): Boolean = transaction {
        table.deleteWhere { table.id eq id } > 0
    }

    /**
     * Удаляет запись с проверкой версии (оптимистичная блокировка).
     *
     * **Алгоритм:**
     * - Удаляет запись с условием `id = :id AND version = :expectedVersion`
     * - Если затронуто 0 строк — проверяет причину конфликта
     *
     * **Когда использовать:** Когда важно убедиться, что вы удаляете именно ту версию,
     * которую получили клиентом (предотвращает случайное удаление после чужих изменений).
     *
     * @param id Идентификатор удаляемой записи
     * @param expectedVersion Ожидаемая версия записи
     * @return `true` если запись удалена (всегда, если не выброшено исключение)
     * @throws NotFoundException Если запись не существует
     * @throws OptimisticLockException Если версия не совпадает
     *
     * @sample
     * ```kotlin
     * // Клиент получил версию 2, хочет удалить
     * userRepository.deleteWithVersion(1, 2)
     * ```
     */
    open fun deleteWithVersion(id: Long, expectedVersion: Long): Boolean {
        val deleted = transaction {
            table.deleteWhere {
                (table.id eq id) and (table.version eq expectedVersion)
            }
        }
        if (deleted == 0) throwLockOrNotFound(id, expectedVersion)
        return true
    }

    /**
     * Удаляет **все** записи из таблицы.
     *
     * **⚠️ ОПАСНО:** Эта операция необратима и удаляет все данные.
     * Использовать только в тестах или административных скриптах!
     *
     * @return Количество удалённых записей
     *
     * @sample
     * ```kotlin
     * // Только для тестов!
     * val deletedCount = repository.deleteAll()
     * ```
     */
    open fun deleteAll(): Int = transaction { table.deleteAll() }

    // ==================== Internal ====================

    /**
     * Извлекает и валидирует поле `version` из JSON-объекта.
     *
     * @param json JSON от клиента
     * @return Значение версии как Long
     * @throws BadRequestException Если поле отсутствует или не является числом
     */
    private fun extractVersion(json: JsonObject): Long {
        val element = json["version"]
            ?: throw BadRequestException("'version' is required for update")
        return element.jsonPrimitive.longOrNull
            ?: throw BadRequestException("'version' must be a number")
    }

    /**
     * Определяет причину неудачного обновления/удаления и выбрасывает соответствующее исключение.
     *
     * **Логика:**
     * - Если запись с указанным id не существует → `NotFoundException`
     * - Если существует, но версия не совпала → `OptimisticLockException`
     *
     * @param id Идентификатор сущности
     * @param expectedVersion Ожидаемая версия
     * @throws NotFoundException Если сущность не найдена
     * @throws OptimisticLockException Если найдена, но с другой версией
     */
    private fun throwLockOrNotFound(id: Long, expectedVersion: Long): Nothing {
        if (!exists(id)) throw NotFoundException("$entityName(id=$id) not found")
        throw OptimisticLockException(entityName, id, expectedVersion)
    }
}
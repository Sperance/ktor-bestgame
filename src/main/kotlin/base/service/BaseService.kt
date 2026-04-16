package base.service

import base.exception.NotFoundException
import base.model.BaseEntity
import base.model.PagedResponse
import base.repository.BaseRepository
import base.table.BaseTable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer
import kotlin.reflect.KProperty1
import server.addons.AppJson

/**
 * Абстрактный базовый сервис с типизированной валидацией и трансформацией.
 *
 * **Жизненный цикл CREATE:**
 * ```
 * JSON от клиента
 *   ↓
 * deserialize(json) → entity
 *   ↓
 * validateCreate(entity)            // проверка бизнес-правил на оригинальных данных
 *   ↓
 * transformCreate(entity) → entity' // подмена/добавление полей через copy()
 *   ↓
 * applyTransform(json, diff)        // изменённые поля накладываются на оригинальный JSON
 *   ↓
 * repository.create(json')
 * ```
 *
 * **Жизненный цикл UPDATE:**
 * ```
 * JSON от клиента
 *   ↓
 * deserialize(json) → entity
 *   ↓
 * validateUpdate(id, entity)
 *   ↓
 * transformUpdate(id, entity) → entity'
 *   ↓
 * applyTransform(json, diff)
 *   ↓
 * repository.update(id, json')
 * ```
 *
 * **Почему diff, а не полная сериализация entity?**
 * При update клиент присылает только изменённые поля (partial update).
 * Если сериализовать весь entity обратно, в JSON попадут Kotlin-дефолты
 * для полей, которые клиент не присылал, и перезатрут реальные значения в БД.
 * Поэтому BaseService сравнивает entity до и после трансформации
 * и накладывает на оригинальный JSON только те поля, которые `transform*` реально изменил.
 *
 * @param E Тип сущности (наследник `BaseEntity`)
 * @param T Тип таблицы (наследник `BaseTable`)
 * @property repository Базовый репозиторий для доступа к данным
 * @property entitySerializer Сериализатор для десериализации JSON → E
 */
abstract class BaseService<E : BaseEntity, T : BaseTable>(
    protected val repository: BaseRepository<E, T>,
    private val entitySerializer: KSerializer<E>
) {

    // ==================== READ ====================

    open fun findAll(): List<E> = repository.findAll()

    open fun findById(id: Long): E? = repository.findById(id)

    open fun getById(id: Long): E =
        findById(id) ?: throw NotFoundException("${entityName()} with id=$id not found")

    // ==================== CREATE ====================

    /**
     * Создаёт новую запись.
     *
     * 1. Десериализует JSON в объект сущности E
     * 2. Вызывает `validateCreate(entity)` — валидация на оригинальных данных
     * 3. Вызывает `transformCreate(entity)` — трансформация через типизированный объект
     * 4. Вычисляет diff и накладывает изменения на оригинальный JSON
     * 5. Передаёт результат в репозиторий
     */
    open fun create(json: JsonObject): E {
        val entity = deserialize(json)
        validateCreate(entity)
        val transformed = transformCreate(entity)
        val finalJson = applyTransform(json, entity, transformed)
        return repository.create(finalJson)
    }

    // ==================== UPDATE ====================

    /**
     * Обновляет запись с оптимистичной блокировкой.
     *
     * 1. Десериализует JSON в объект сущности E
     * 2. Вызывает `validateUpdate(id, entity)` — валидация на оригинальных данных
     * 3. Вызывает `transformUpdate(id, entity)` — трансформация через типизированный объект
     * 4. Вычисляет diff и накладывает изменения на оригинальный JSON
     * 5. Передаёт результат в репозиторий
     */
    open fun update(id: Long, json: JsonObject): E {
        val entity = deserialize(json)
        validateUpdate(id, entity)
        val transformed = transformUpdate(id, entity)
        val finalJson = applyTransform(json, entity, transformed)
        return repository.update(id, finalJson)
    }

    // ==================== DELETE ====================

    open fun delete(id: Long) {
        if (!repository.exists(id)) {
            throw NotFoundException("${entityName()} with id=$id not found")
        }
        repository.delete(id)
    }

    open fun deleteWithVersion(id: Long, version: Long) {
        repository.deleteWithVersion(id, version)
    }

    // ==================== UTILITY ====================

    open fun count(): Long = repository.count()

    open fun exists(id: Long): Boolean = repository.exists(id)

    open fun findPaged(page: Int, pageSize: Int = 20): PagedResponse<E> {
        val items = repository.findPaged(page, pageSize)
        val total = repository.count()
        val pages = if (pageSize > 0) ((total + pageSize - 1) / pageSize).toInt() else 0
        return PagedResponse(items, page, pageSize, total, pages)
    }

    // ==================== HOOKS: Validation ====================

    /**
     * Валидация перед созданием. Получает десериализованный объект
     * с **оригинальными** данными клиента (до трансформации).
     *
     * Бросайте исключение, если данные не проходят проверку.
     *
     * ```kotlin
     * override fun validateCreate(entity: User) {
     *     repo.findByEmail(entity.email)?.let {
     *         throw ConflictException("Email '${entity.email}' is already taken")
     *     }
     *     if (entity.password.length < 6) {
     *         throw BadRequestException("Password must be at least 6 characters")
     *     }
     * }
     * ```
     */
    protected open fun validateCreate(entity: E) {}

    /**
     * Валидация перед обновлением. Получает id и десериализованный объект
     * с **оригинальными** данными клиента (до трансформации).
     *
     * Поля, не присланные клиентом, будут заполнены Kotlin-дефолтами класса.
     *
     * ```kotlin
     * override fun validateUpdate(id: Long, entity: User) {
     *     entity.email?.let { email ->
     *         repo.findByEmail(email)?.let { existing ->
     *             if (existing.id != id) throw ConflictException("Email taken")
     *         }
     *     }
     * }
     * ```
     */
    protected open fun validateUpdate(id: Long, entity: E) {}

    // ==================== HOOKS: Transform ====================

    /**
     * Трансформация сущности **перед** записью в БД при создании.
     * Вызывается **после** `validateCreate`, поэтому данные уже провалидированы.
     *
     * Получает типизированный объект — можно работать с полями напрямую
     * и возвращать изменённую копию через `copy()`.
     *
     * BaseService автоматически вычислит, какие поля изменились,
     * и применит только их к оригинальному JSON перед записью.
     *
     * По умолчанию возвращает объект без изменений.
     *
     * ```kotlin
     * override fun transformCreate(entity: User): User {
     *     val salt = generateSalt()
     *     return entity.copy(
     *         password = hashPassword(entity.password, salt),
     *         salt = salt
     *     )
     * }
     * ```
     *
     * @param entity Десериализованный объект с оригинальными данными клиента
     * @return Трансформированный объект (изменённые поля будут применены к JSON)
     */
    protected open fun transformCreate(entity: E): E = entity

    /**
     * Трансформация сущности **перед** записью в БД при обновлении.
     * Вызывается **после** `validateUpdate`, поэтому данные уже провалидированы.
     *
     * Получает типизированный объект — можно работать с полями напрямую.
     * Поля, не присланные клиентом, заполнены Kotlin-дефолтами класса.
     *
     * BaseService автоматически вычислит diff и применит к оригинальному JSON
     * только те поля, которые были реально изменены в `transform`.
     * Поля, не тронутые трансформацией, останутся как есть в JSON клиента.
     *
     * По умолчанию возвращает объект без изменений.
     *
     * ```kotlin
     * override fun transformUpdate(id: Long, entity: User): User {
     *     if (entity.password.isEmpty()) return entity  // пароль не меняется
     *     val salt = generateSalt()
     *     return entity.copy(
     *         password = hashPassword(entity.password, salt),
     *         salt = salt
     *     )
     * }
     * ```
     *
     * @param id Идентификатор обновляемой записи
     * @param entity Десериализованный объект с оригинальными данными клиента
     * @return Трансформированный объект (изменённые поля будут применены к JSON)
     */
    protected open fun transformUpdate(id: Long, entity: E): E = entity

    // ==================== OTHER ====================

    /**
     * Имя сущности для сообщений об ошибках.
     */
    protected open fun entityName(): String = "Entity"

    // ==================== INTERNAL ====================

    /**
     * Десериализует JsonObject в объект сущности E.
     */
    private fun deserialize(json: JsonObject): E =
        AppJson.decodeFromJsonElement(entitySerializer, json)

    /**
     * Сериализует объект сущности E в JsonObject.
     */
    private fun serialize(entity: E): JsonObject =
        AppJson.encodeToJsonElement(entitySerializer, entity) as JsonObject

    /**
     * Вычисляет diff между [before] и [after], накладывает изменённые поля
     * на [originalJson].
     *
     * Если transform не изменил объект (before === after или все поля совпадают),
     * возвращает оригинальный JSON без изменений — быстрый путь без сериализации.
     *
     * @param originalJson JSON от клиента (partial — только присланные поля)
     * @param before Объект до трансформации
     * @param after Объект после трансформации
     * @return JSON с наложенным diff
     */
    private fun applyTransform(originalJson: JsonObject, before: E, after: E): JsonObject {
        // Быстрый путь: трансформация ничего не изменила
        if (before === after) return originalJson

        val beforeJson = serialize(before)
        val afterJson = serialize(after)

        // Ещё одна проверка: все поля совпадают
        if (beforeJson == afterJson) return originalJson

        // Накладываем на оригинальный JSON только изменённые поля
        val result = originalJson.toMutableMap()
        for ((key, newValue) in afterJson) {
            if (beforeJson[key] != newValue) {
                result[key] = newValue
            }
        }

        return JsonObject(result)
    }
}
package base.service

import base.exception.NotFoundException
import base.model.BaseEntity
import base.model.PagedResponse
import base.repository.BaseRepository
import base.table.BaseTable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import server.addons.AppJson

/**
 * Абстрактный базовый сервис с типизированной валидацией.
 *
 * Хуки `validateCreate(entity)` и `validateUpdate(id, entity)` получают
 * десериализованный Kotlin-объект вместо сырого JsonObject, что позволяет
 * работать с полями напрямую: `entity.email`, `entity.userId` и т.д.
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
     * 2. Вызывает `validateCreate(entity)` с типизированным объектом
     * 3. Передаёт оригинальный JSON
     */
    open fun create(json: JsonObject): E {
        val entity = deserialize(json)
        validateCreate(entity)
        return repository.create(json)
    }

    // ==================== UPDATE ====================

    /**
     * Обновляет запись с оптимистичной блокировкой.
     *
     * 1. Десериализует JSON в объект сущности E
     * 2. Вызывает `validateUpdate(id, entity)` с типизированным объектом
     * 3. Передаёт оригинальный JSON в репозиторий
     */
    open fun update(id: Long, json: JsonObject): E {
        val entity = deserialize(json)
        validateUpdate(id, entity)
        return repository.update(id, json)
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

    // ==================== HOOKS ====================

    /**
     * Валидация перед созданием. Получает десериализованный объект.
     *
     * ```kotlin
     * override fun validateCreate(entity: User) {
     *     repo.findByEmail(entity.email)?.let {
     *         throw ConflictException("Email '${entity.email}' is already taken")
     *     }
     * }
     * ```
     */
    protected open fun validateCreate(entity: E) {}

    /**
     * Валидация перед обновлением. Получает id и десериализованный объект
     * с полями, которые клиент хочет обновить.
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

    /**
     * Имя сущности для сообщений об ошибках.
     */
    protected open fun entityName(): String = "Entity"

    // ==================== INTERNAL ====================

    /**
     * Десериализует JsonObject в объект сущности E.
     * Использует AppJson (ignoreUnknownKeys = true, encodeDefaults = true),
     * поэтому отсутствующие поля заполняются Kotlin-дефолтами класса.
     */
    private fun deserialize(json: JsonObject): E =
        AppJson.decodeFromJsonElement(entitySerializer, json)
}

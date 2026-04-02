package base.service

import base.exception.NotFoundException
import base.model.BaseEntity
import base.model.PagedResponse
import base.repository.BaseRepository
import base.table.BaseTable
import kotlinx.serialization.json.JsonObject

/**
 * Бизнес-логика поверх репозитория.
 * Хуки validateCreate / validateUpdate переопределяются в наследниках.
 */
abstract class BaseService<E : BaseEntity, T : BaseTable>(
    protected val repository: BaseRepository<E, T>
) {

    open fun findAll(): List<E> = repository.findAll()

    open fun findById(id: Long): E? = repository.findById(id)

    open fun getById(id: Long): E =
        findById(id) ?: throw NotFoundException("${entityName()} with id=$id not found")

    /**
     * Создание. Валидация вызывается до insert.
     */
    open fun create(json: JsonObject): E {
        validateCreate(json)
        return repository.create(json)
    }

    /**
     * Обновление с optimistic locking.
     */
    open fun update(id: Long, json: JsonObject): E {
        validateUpdate(id, json)
        return repository.update(id, json)
    }

    open fun delete(id: Long) {
        if (!repository.exists(id)) {
            throw NotFoundException("${entityName()} with id=$id not found")
        }
        repository.delete(id)
    }

    open fun deleteWithVersion(id: Long, version: Long) {
        repository.deleteWithVersion(id, version)
    }

    open fun count(): Long = repository.count()
    open fun exists(id: Long): Boolean = repository.exists(id)

    open fun findPaged(page: Int, pageSize: Int = 20): PagedResponse<E> {
        val items = repository.findPaged(page, pageSize)
        val total = repository.count()
        val pages = if (pageSize > 0) ((total + pageSize - 1) / pageSize).toInt() else 0
        return PagedResponse(items, page, pageSize, total, pages)
    }

    // ========== Hooks — переопределяются в наследниках ==========

    protected open fun validateCreate(json: JsonObject) {}
    protected open fun validateUpdate(id: Long, json: JsonObject) {}
    protected open fun entityName(): String = "Entity"
}
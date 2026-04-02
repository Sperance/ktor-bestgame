package base.service

import base.exception.NotFoundException
import base.model.BaseEntity
import base.model.CreateRequest
import base.model.PagedResponse
import base.model.UpdateRequest
import base.repository.BaseRepository
import base.table.BaseTable

/**
 * Бизнес-логика поверх репозитория.
 * Хуки validateCreate / validateUpdate переопределяются в наследниках.
 */
abstract class BaseService<E : BaseEntity, CQ : CreateRequest, UQ : UpdateRequest, T : BaseTable>(
    protected val repository: BaseRepository<E, CQ, UQ, T>
) {

    open fun findAll(): List<E> = repository.findAll()

    open fun findById(id: Long): E? = repository.findById(id)

    open fun getById(id: Long): E =
        findById(id) ?: throw NotFoundException("${entityName()} with id=$id not found")

    open fun create(request: CQ): E {
        validateCreate(request)
        return repository.create(request)
    }

    open fun createBatch(requests: List<CQ>): List<E> {
        requests.forEach(::validateCreate)
        return repository.createBatch(requests)
    }

    /**
     * Optimistic-lock update.
     * Если version не совпала — OptimisticLockException → HTTP 409.
     */
    open fun update(id: Long, request: UQ): E {
        validateUpdate(id, request)
        return repository.update(id, request)
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

    // ========== Hooks ==========

    protected open fun validateCreate(request: CQ) {}
    protected open fun validateUpdate(id: Long, request: UQ) {}

    /** Имя сущности для сообщений об ошибках */
    protected open fun entityName(): String = "Entity"
}
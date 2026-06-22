package features

import base.exception.NotFoundException
import base.model.PagedResponse
import com.mongodb.client.result.InsertOneResult
import com.mongodb.kotlin.client.coroutine.ClientSession
import org.bson.types.ObjectId

abstract class BaseServiceMongo<T : VersionedEntity>(
    protected val repository: BaseRepositoryMongo<T>
) {

    // ==================== READ ====================

    open suspend fun findAll(): List<T> = repository.findAll()

    open suspend fun findById(id: String): T? = repository.findById(id)

    // ==================== CREATE ====================

    open suspend fun create(entity: T, session: ClientSession): T {
        return repository.insert(entity, session)
    }

    // ==================== UPDATE ====================

    open suspend fun update(entity: T, session: ClientSession): T? {
        return repository.updateAndReturn(entity, session)
    }

    open suspend fun update(id: String, session: ClientSession): T? {
        return repository.updateAndReturn(id, session)
    }

    // ==================== DELETE ====================

    open suspend fun delete(id: String) {
        if (!repository.exists(ObjectId(id))) {
            throw NotFoundException("${repository.collectionName} with id=$id not found")
        }
        repository.deleteById(ObjectId(id))
    }

    // ==================== UTILITY ====================

    open suspend fun count(): Long = repository.count()

    open suspend fun exists(id: String): Boolean = repository.exists(ObjectId(id))

    open suspend fun findPaged(page: Int, pageSize: Int = 20): PagedResponse<T> {
        val items = repository.findLimited(page, pageSize)
        val total = repository.count()
        val pages = if (pageSize > 0) ((total + pageSize - 1) / pageSize).toInt() else 0
        return PagedResponse(items, page, pageSize, total, pages)
    }
}
package ru.descend.infrastructure.mongo

import com.mongodb.MongoBulkWriteException
import com.mongodb.MongoWriteException
import com.mongodb.ReadConcern
import com.mongodb.bulk.BulkWriteResult
import com.mongodb.client.model.*
import com.mongodb.client.model.changestream.ChangeStreamDocument
import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import ru.descend.infrastructure.mongo.MongoFactory
import ru.descend.shared.CONST_FIELD_DELETED
import ru.descend.shared.CONST_FIELD_ID
import ru.descend.shared.CONST_FIELD_UPDATED
import ru.descend.shared.CONST_FIELD_VERSION
import ru.descend.shared.CONST_SYSTEM_FIELDS
import ru.descend.shared.error.BaseRepositoryExceptions
import ru.descend.shared.extensions.now
import ru.descend.shared.extensions.printLog
import ru.descend.shared.http.PagedMongoResponse
import ru.descend.shared.model.StockEntity
import ru.descend.shared.model.VersionedEntity

/**
 * Конфигурация уникального индекса для MongoDB.
 * 
 * @property indexName Имя индекса (должно быть уникальным в пределах коллекции)
 * @property fields Список полей, по которым создается индекс
 * @property sparse Флаг разреженного индекса (игнорирует документы без указанных полей)
 */
data class UniqueIndexConfig(
    val indexName: String,
    val fields: List<String>,
    val sparse: Boolean = false
)

/**
 * Абстрактный репозиторий, реализующий базовые CRUD-операции для сущностей MongoDB.
 * 
 * Поддерживает:
 * - Оптимистичную блокировку через поле version (concurrency control)
 * - Мягкое удаление (soft delete) через поле deleted
 * - Системные поля: _id, version, deleted, updated
 * - Транзакции через ClientSession
 * - Индексацию для ускорения операций
 * 
 * @param T Тип сущности, наследуемый от StockEntity
 * 
 * Пример использования:
 * ```
 * class UserRepository : BaseRepository<UserMongo>(UserMongo::class) {
 *     override suspend fun validateBeforeInsert(entity: UserMongo) {
 *         // Валидация перед вставкой
 *     }
 * }
 * 
 * // Инициализация
 * val repo = UserRepository()
 * repo.initialize(
 *     uniqueIndexes = listOf(
 *         UniqueIndexConfig("unique_email", listOf("email"), sparse = true)
 *     )
 * )
 * ```
 */
abstract class BaseRepository<T : StockEntity>(entityClass: KClass<T>) {

    private val collectionName = entityClass.simpleName!!

    var collection: MongoCollection<T> = MongoFactory.getDatabase().getCollection(collectionName, entityClass.java)

    fun initialize(uniqueIndexes: List<UniqueIndexConfig> = emptyList()) {
        runBlocking {
            setupUniqueIndexes(uniqueIndexes)
            setupVersionIndex()
        }
    }

    private suspend fun setupUniqueIndexes(indexes: List<UniqueIndexConfig>) {
        indexes.forEach { config ->
            try {
                val indexOptions = IndexOptions()
                    .unique(true)
                    .name(config.indexName)
                    .apply {
                        if (config.sparse) sparse(true)
                    }

                val indexName = collection.createIndex(
                    Indexes.ascending(*config.fields.toTypedArray()),
                    indexOptions
                )
                printLog("✅ [$collectionName] Уникальный индекс создан: $indexName на полях ${config.fields}")
            } catch (e: MongoWriteException) {
            if (e.hasErrorLabel("TransientTransactionError")) throw e
                if (e.code == 85) { // IndexAlreadyExists
                    printLog("ℹ️ Индекс ${config.indexName} уже существует")
                } else {
                    throw BaseRepositoryExceptions.funException("setupUniqueIndexes", e.message)
                }
            }
        }
    }

    private suspend fun setupVersionIndex() {
        try {
            collection.createIndex(Indexes.ascending(CONST_FIELD_VERSION))
        } catch (_: Exception) {
            // Индекс уже существует - игнорируем
        }
    }

    suspend fun insert(entity: T, session: ClientSession, validation: Boolean = true): T {
        if (entity is VersionedEntity && entity.version != 0L) throw BaseRepositoryExceptions.funExceptionInsertVersion("insert", entity.version.toString())
        if (validation) validateBeforeInsert(entity, session)

        return try {
            val result = collection.insertOne(session, entity)
            if (!result.wasAcknowledged() || result.insertedId == null) {
                throw BaseRepositoryExceptions.funExceptionInsertInvalid("insert")
            }
            printLog("[ADDED::$collectionName] ${result.insertedId}")
            entity._id = result.insertedId!!.asString().value

            if (validation) validateAfterInsert(entity, session)

            entity
        } catch (e: MongoWriteException) {
            if (e.hasErrorLabel("TransientTransactionError")) throw e
            if (e.code == 11000) {
                throw BaseRepositoryExceptions.funExceptionRace("insert", e.message)
            }
            throw BaseRepositoryExceptions.funException("insert", e.message)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException || e is com.mongodb.MongoException && e.hasErrorLabel("TransientTransactionError")) throw e
            throw BaseRepositoryExceptions.funException("insert", e.message)
        }
    }

    suspend fun insertMany(entities: List<T>, session: ClientSession): List<T> {

        if (entities.isEmpty()) return emptyList()

        entities.forEach {
            if (it is VersionedEntity && it.version != 0L)
                throw BaseRepositoryExceptions.funExceptionInsertVersion("insertMany", it.version.toString())
            validateBeforeInsert(it, session)
        }

        return try {
            val result = collection.insertMany(session, entities)
            printLog("[ADDED_MANY::$collectionName] size: ${result.insertedIds.size}")

            // Присваиваем сгенерированные ID объектам
            entities.forEachIndexed { index, entity ->
                result.insertedIds[index]?.let { bsonValue ->
                    entity._id = bsonValue.asString().value

                    validateAfterInsert(entity, session)

                    printLog("[ADDED_MANY::$collectionName] ${entity._id}")
                }
            }

            entities  // ← возвращаем список объектов с присвоенными ID
        } catch (e: MongoWriteException) {
            if (e.hasErrorLabel("TransientTransactionError")) throw e
            if (e.code == 11000) {
                throw BaseRepositoryExceptions.funExceptionRace("insertMany", e.message)
            }
            throw BaseRepositoryExceptions.funException("insertMany", e.message)
        } catch (e: MongoBulkWriteException) {
            if (e.hasErrorLabel("TransientTransactionError")) throw e
            throw BaseRepositoryExceptions.funException("insertMany", e.writeErrors.firstOrNull()?.message?:e.message)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException || e is com.mongodb.MongoException && e.hasErrorLabel("TransientTransactionError")) throw e
            throw BaseRepositoryExceptions.funException("insertMany", e.message)
        }
    }

    suspend fun findById(id: String): T? {
        return collection.find(Filters.eq(CONST_FIELD_ID, id)).firstOrNull()
    }

    suspend fun findById(id: String, session: ClientSession): T? {
        return collection.find(session, Filters.eq(CONST_FIELD_ID, id)).firstOrNull()
    }

    suspend fun findByIdForUpdate(id: ObjectId): T? {
        return collection
            .withReadConcern(ReadConcern.MAJORITY)
            .find(Filters.eq(CONST_FIELD_ID, id.toHexString()))
            .firstOrNull()
    }

    suspend fun findAll(): List<T> {
        return collection.find().toList()
    }

    suspend fun findAll(session: ClientSession): List<T> {
        return collection.find(session).toList()
    }

    suspend fun findLimited(limit: Int, skip: Int = 0): List<T> {
        return collection.find()
            .skip(skip)
            .limit(limit)
            .toList()
    }

    fun findByFilterFlow(filter: Bson): Flow<T> {
        return collection.find(filter)
    }

    suspend fun <S> findByField(field: KProperty1<T, S>, value: S): T? {
        return collection.find(Filters.eq(field.name, value)).firstOrNull()
    }

    suspend fun <S> findByField(field: KProperty1<T, S>, value: S, session: ClientSession): T? {
        return collection.find(session, Filters.eq(field.name, value)).firstOrNull()
    }

    suspend fun <S> findByFieldList(field: KMutableProperty1<T, S>, value: S): List<T> {
        return collection.find(Filters.eq(field.name, value)).toList()
    }

    suspend fun findByFilter(filter: Bson): List<T> {
        return collection.find(filter).toList()
    }

    fun watchAll(): Flow<ChangeStreamDocument<T>> {
        return collection.watch().map { it }
    }

    private fun versionFilter(expected: Long): org.bson.conversions.Bson = if (expected == 0L)
        Filters.or(Filters.eq(CONST_FIELD_VERSION, 0L), Filters.exists(CONST_FIELD_VERSION, false))
        else Filters.eq(CONST_FIELD_VERSION, expected)

    suspend fun validateApiUpdate(fields: Map<String, Any?>) = validateBeforeUpdate(fields)

    suspend fun update(entity: T, session: ClientSession): UpdateResult {
        val expectedVersion = if (entity is VersionedEntity) entity.version else 0L
        val newVersion = expectedVersion + 1

        val filter = Filters.and(
            Filters.eq(CONST_FIELD_ID, entity._id),
            if (entity is VersionedEntity) versionFilter(expectedVersion) else Filters.empty(),
        )

        val update = Updates.combine(
            if (entity is VersionedEntity) Updates.set(CONST_FIELD_VERSION, newVersion) else Filters.empty(),
            if (entity is VersionedEntity) Updates.set(CONST_FIELD_UPDATED, LocalDateTime.now()) else Filters.empty(),
            *getUpdateFields(entity).map { (field, value) ->
                Updates.set(field, value)
            }.toTypedArray()
        )

        val result = try {
            collection.updateOne(session, filter, update)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException || e is com.mongodb.MongoException && e.hasErrorLabel("TransientTransactionError")) throw e
            throw BaseRepositoryExceptions.funException("update", e.message)
        }

        if (result.matchedCount == 0L) {
            val existing = findById(entity._id)
            if (existing == null) {
                throw BaseRepositoryExceptions.funExceptionFindId("update", entity._id)
            } else {
                ru.descend.shared.http.conflict()
            }
        }

        if (entity is VersionedEntity) {
            if (result.wasAcknowledged() && result.modifiedCount > 0) {
                entity.version = newVersion
            }
        }

        validateAfterUpdate(entity, session)

        return result
    }

    suspend fun updateFields(
        entity: T,
        updates: Map<String, Any?>,
        session: ClientSession
    ): T {
        //Проверки Мапы новых полей
        validateBeforeUpdate(updates)

        val expectedVersion = if (entity is VersionedEntity) entity.version else 0L
        val newVersion = expectedVersion + 1

        //Фильтр для поиска нужного объекта по ID и version
        val filter = Filters.and(
            Filters.eq(CONST_FIELD_ID, entity._id),
            if (entity is VersionedEntity) versionFilter(expectedVersion) else Filters.empty()
        )

        //Вручную указываем поля, которые нужно обновить
        val updatesList = mutableListOf<Bson>()

        if (entity is VersionedEntity) {
            updatesList.add(Updates.set(CONST_FIELD_VERSION, newVersion))
            updatesList.add(Updates.set(CONST_FIELD_UPDATED, LocalDateTime.now()))
        }

        //Заполняем поля которые нужно изменять в конечном объекте
        updates.forEach { (field, value) ->
            if (field != CONST_FIELD_ID && field != CONST_FIELD_VERSION) {
                updatesList.add(Updates.set(field, value))
            }
        }

        // Добавляем опцию для возврата ОБНОВЛЕННОГО документа
        val options = FindOneAndUpdateOptions()
            .returnDocument(ReturnDocument.AFTER)

        val update = Updates.combine(updatesList)
        printLog("[UPDATE::$collectionName] id: ${entity._id}")

        val result = try {
            collection.findOneAndUpdate(session, filter, update, options)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException || e is com.mongodb.MongoException && e.hasErrorLabel("TransientTransactionError")) throw e
            throw BaseRepositoryExceptions.funException("updateFields", e.message)
        }

        if (result == null) {
            throw BaseRepositoryExceptions.funException("updateFields", "Not found object with id ${entity._id} after update")
        }

        validateAfterUpdate(result, session)

        return result
    }

    open suspend fun updateFields(
        id: String,
        fields: Map<String, Any?>,
        session: ClientSession
    ): T? {
        val entity = findById(id) ?: throw BaseRepositoryExceptions.funExceptionFindId("updateFields", id)
        return updateFields(entity, fields, session)
    }

    suspend fun deleteById(id: String, session: ClientSession): DeleteResult {
        val findedObj = findById(id)
        return deleteWithVersion(findedObj, session)
    }

    suspend fun deleteById(entity: T, session: ClientSession): DeleteResult {
        return deleteWithVersion(entity, session)
    }

    suspend fun deleteWithVersion(entity: T?, session: ClientSession): DeleteResult {
        printLog("[DELETE::$collectionName] id: ${entity?._id}")
        if (entity == null) {
            throw BaseRepositoryExceptions.funExceptionEntityNull("deleteWithVersion")
        }

        val filter = Filters.and(
            Filters.eq(CONST_FIELD_ID, entity._id),
            if (entity is VersionedEntity) Filters.eq(CONST_FIELD_VERSION, entity.version) else Filters.empty()
        )

        val result = try {
            collection.deleteOne(session, filter)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException || e is com.mongodb.MongoException && e.hasErrorLabel("TransientTransactionError")) throw e
            throw BaseRepositoryExceptions.funException("deleteWithVersion", e.message)
        }

        validateAfterDelete(entity, session, false)

        if (result.deletedCount == 0L) {
            val existing = findById(entity._id)
            if (existing != null) {
                throw BaseRepositoryExceptions.funExceptionRace("deleteWithVersion", "Entity version mismatch. Current: ${if (entity is VersionedEntity) entity.version else 0L} need: ${if (existing is VersionedEntity) existing.version else 0L}")
            }
        }

        return result
    }

    suspend fun softDelete(id: String, session: ClientSession): UpdateResult {
        printLog("[SOFT_DELETE::$collectionName] id: $id")

        val findedObj = findById(id)
        if (findedObj == null) {
            throw BaseRepositoryExceptions.funExceptionFindId("softDelete", id)
        }

        if (findedObj !is VersionedEntity) {
            throw BaseRepositoryExceptions.funExceptionVersioned("softDelete", findedObj::class.simpleName)
        }

        val filter = Filters.eq(CONST_FIELD_ID, id)
        val update = Updates.set(CONST_FIELD_DELETED, true)

        val result = try {
            collection.updateOne(session, filter, update)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException || e is com.mongodb.MongoException && e.hasErrorLabel("TransientTransactionError")) throw e
            throw BaseRepositoryExceptions.funException("softDelete", e.message)
        }
        validateAfterDelete(findedObj, session, true)

        return result
    }

    suspend fun softDelete(entity: T, session: ClientSession): UpdateResult {
        return softDelete(entity._id, session)
    }

    suspend fun restore(id: String, session: ClientSession): UpdateResult {
        printLog("[RESTORE::$collectionName] id: $id")
        val filter = Filters.eq(CONST_FIELD_ID, id)
        val update = Updates.set(CONST_FIELD_DELETED, false)
        val result = try {
            collection.updateOne(session, filter, update)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException || e is com.mongodb.MongoException && e.hasErrorLabel("TransientTransactionError")) throw e
            throw BaseRepositoryExceptions.funException("restore", e.message)
        }
        return result
    }

    suspend fun restore(entity: T, session: ClientSession): UpdateResult {
        return restore(entity._id, session)
    }

    suspend fun bulkUpdate(entities: List<T>, session: ClientSession): BulkWriteResult {
        val requests = entities.map { entity ->
            val filter = Filters.and(
                Filters.eq(CONST_FIELD_ID, entity._id),
                if (entity is VersionedEntity) Filters.eq(CONST_FIELD_VERSION, entity.version) else Filters.empty()
            )
            val update = Updates.combine(
                if (entity is VersionedEntity) Updates.set(CONST_FIELD_VERSION, entity.version + 1) else Filters.empty(),
                *getUpdateFields(entity).map { (field, value) ->
                    Updates.set(field, value)
                }.toTypedArray()
            )

            UpdateOneModel<T>(filter, update)
        }

        val result = try {
            collection.bulkWrite(session, requests)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException || e is com.mongodb.MongoException && e.hasErrorLabel("TransientTransactionError")) throw e
            throw BaseRepositoryExceptions.funException("bulkUpdate", e.message)
        }

        return result
    }

    suspend fun exists(id: String, withDeleted: Boolean = false): Boolean {
        return try {
            val filter = Filters.and(
                Filters.eq(CONST_FIELD_ID, id),
                if (withDeleted) Filters.empty() else Filters.ne(CONST_FIELD_DELETED, true)
            )

            collection.find(filter).firstOrNull() != null
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException || e is com.mongodb.MongoException && e.hasErrorLabel("TransientTransactionError")) throw e
            throw BaseRepositoryExceptions.funException("exists", e.message)
        }
    }

    suspend fun count(filter: Bson = Filters.empty()): Long {
        return collection.countDocuments(filter)
    }

    suspend fun count(session: ClientSession, filter: Bson = Filters.empty()): Long {
        return collection.countDocuments(session, filter)
    }

    suspend fun findPaged(page: Int, pageSize: Int = 20): PagedMongoResponse<T> {
        require(page >= 0 && pageSize in 1..200) { "Invalid pagination" }
        val offset = Math.multiplyExact(page, pageSize)
        val items = findLimited(limit = pageSize, skip = offset)
        val total = count()
        val pages = ((total + pageSize - 1) / pageSize).toInt()
        return PagedMongoResponse(items, page, pageSize, total, pages)
    }

    private fun getUpdateFields(entity: T): Map<String, Any?> {
        val fields = mutableMapOf<String, Any?>()

        entity::class.java.kotlin.memberProperties.forEach { property ->
            val fieldName = property.name
            if (fieldName !in CONST_SYSTEM_FIELDS) {
                val value = property.getter.call(entity)
                fields[fieldName] = value
            }
        }

        return fields
    }

    suspend fun deleteAll() {
        printLog("[DELETE_All::$collectionName]")
        collection.drop()
    }

    protected open suspend fun validateBeforeInsert(entity: T, session: ClientSession) {

    }

    protected open suspend fun validateBeforeUpdate(changes: Map<String, Any?>) {
    }

    protected open suspend fun validateAfterUpdate(entity: T, session: ClientSession) {

    }

    protected open suspend fun validateAfterInsert(entity: T, session: ClientSession) {

    }

    protected open suspend fun validateAfterDelete(entity: T, session: ClientSession, softDelete: Boolean) {

    }
}

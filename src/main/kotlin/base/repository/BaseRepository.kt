package base.repository

import base.route.PagedMongoResponse
import com.mongodb.MongoWriteException
import com.mongodb.ReadConcern
import com.mongodb.bulk.BulkWriteResult
import com.mongodb.client.model.*
import com.mongodb.client.model.changestream.ChangeStreamDocument
import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoCollection
import config.MongoFactory
import extensions.CONST_FIELD_DELETED
import extensions.CONST_FIELD_ID
import extensions.CONST_FIELD_UPDATED
import extensions.CONST_FIELD_VERSION
import extensions.CONST_SYSTEM_FIELDS
import extensions.now
import extensions.printLog
import base.entity.VersionedEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.memberProperties

/**
 * Конфигурация уникального индекса.
 * @property indexName Уникальное имя индекса
 * @property fields Список полей для индексации
 * @property sparse Если true, индексирует только документы, содержащие указанные поля
 */
data class UniqueIndexConfig(
    val indexName: String,
    val fields: List<String>,
    val sparse: Boolean = false
)

/**
 * Базовый репозиторий для работы с MongoDB.
 * Предоставляет CRUD операции с поддержкой:
 * - Уникальных индексов
 * - Оптимистичной блокировки через поле version
 * - Мягкого удаления через поле deleted
 * - Транзакций через ClientSession
 * - Type-safe DSL через KtMongo
 * - Change streams для реактивного программирования
 *
 * @param T Тип сущности, должен наследовать VersionedEntity
 * @param collectionName Имя коллекции в MongoDB
 * @param entityClass KClass сущности для рефлексии
 */
abstract class BaseRepository<T : VersionedEntity>(private val entityClass: KClass<T>) {

    private val collectionName = entityClass.simpleName!!

    // ==================== ПОЛЯ ====================

    /** Коллекция официального драйвера MongoDB для низкоуровневых операций */
    lateinit var collection: MongoCollection<T>

    // ==================== ИНИЦИАЛИЗАЦИЯ ====================

    /**
     * Инициализирует репозиторий: создаёт коллекцию и индексы.
     * Должен быть вызван перед использованием репозитория.
     *
     * @param uniqueIndexes Список уникальных индексов для создания
     *
     * Пример:
     * ```
     * repo.initialize(
     *     uniqueIndexes = listOf(
     *         UniqueIndexConfig("unique_email", listOf("email"))
     *     )
     * )
     * ```
     */
    fun initialize(uniqueIndexes: List<UniqueIndexConfig> = emptyList()) {
        collection = MongoFactory.db.getCollection(collectionName, entityClass.java)
        runBlocking {
            setupUniqueIndexes(uniqueIndexes)
            setupVersionIndex()
        }
    }

    /**
     * Создаёт уникальные индексы для указанных полей.
     * Если индекс уже существует, сообщает об этом и продолжает работу.
     */
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
                if (e.code == 85) { // IndexAlreadyExists
                    printLog("ℹ️ Индекс ${config.indexName} уже существует")
                } else {
                    throw BaseRepositoryExceptions.funException("setupUniqueIndexes", e.message)
                }
            }
        }
    }

    /**
     * Создаёт индекс для поля version для ускорения проверок при обновлении.
     */
    private suspend fun setupVersionIndex() {
        try {
            collection.createIndex(Indexes.ascending(CONST_FIELD_VERSION))
        } catch (e: Exception) {
            // Индекс уже существует - игнорируем
        }
    }

    // ==================== CREATE ОПЕРАЦИИ ====================

    /**
     * Вставляет один документ в коллекцию.
     *
     * @param entity Сущность для вставки
     * @param session Сессия транзакции (опционально)
     * @return Результат вставки с информацией об insertedId
     * @throws IllegalArgumentException Если version != 0
     *
     * Пример:
     * ```
     * val result = repo.insert(UserMongo(email = "test@email.com", name = "Test", age = 25))
     * ```
     */
    suspend fun insert(entity: T, session: ClientSession): T {
        if (entity.version != 0L) throw BaseRepositoryExceptions.funExceptionInsertVersion("insert", entity.version.toString())
        validateBeforeInsert(entity)

        return try {
            val result = collection.insertOne(session, entity)
            if (!result.wasAcknowledged() || result.insertedId == null) {
                throw BaseRepositoryExceptions.funExceptionInsertInvalid("insert")
            }
            printLog("[ADDED::$collectionName] ${result.insertedId} object: $entity")
            entity.setId(result.insertedId!!.asObjectId().value.toHexString())

            validateAfterInsert(entity, session)

            entity
        } catch (e: MongoWriteException) {
            if (e.code == 11000) {
                throw BaseRepositoryExceptions.funExceptionRace("insert", e.message)
            }
            throw BaseRepositoryExceptions.funException("insert", e.message)
        } catch (e: Exception) {
            throw BaseRepositoryExceptions.funException("insert", e.message)
        }
    }

    /**
     * Вставляет несколько документов в коллекцию атомарно.
     *
     * @param entities Список сущностей для вставки
     * @param session Сессия транзакции
     * @return Результат массовой вставки
     */
    suspend fun insertMany(entities: List<T>, session: ClientSession): List<T> {
        entities.forEach {
            if (it.version != 0L) throw BaseRepositoryExceptions.funExceptionInsertVersion("insertMany", it.version.toString())
            validateBeforeInsert(it)
        }

        return try {
            val result = collection.insertMany(session, entities)
            printLog("[ADDED_MANY::$collectionName] size: ${result.insertedIds.size}")

            // Присваиваем сгенерированные ID объектам
            entities.forEachIndexed { index, entity ->
                result.insertedIds[index]?.let { bsonValue ->
                    entity._id = bsonValue.asObjectId().value

                    validateAfterInsert(entity, session)

                    printLog("\t[ADDED_MANY::$collectionName] ${entity._id} object: $entity")
                }
            }

            entities  // ← возвращаем список объектов с присвоенными ID
        } catch (e: MongoWriteException) {
            if (e.code == 11000) {
                throw BaseRepositoryExceptions.funExceptionRace("insertMany", e.message)
            }
            throw BaseRepositoryExceptions.funException("insertMany", e.message)
        } catch (e: Exception) {
            throw BaseRepositoryExceptions.funException("insertMany", e.message)
        }
    }

    // ==================== READ ОПЕРАЦИИ ====================

    /**
     * Поиск документа по ID.
     * Использует readConcern LOCAL (быстро, но может вернуть неподтверждённые данные).
     * Подходит для обычных чтений, где не критична абсолютная свежесть.
     *
     * @param id ID документа
     * @return Найденный документ или null
     */
    suspend fun findById(id: ObjectId): T? {
        return collection.find(Filters.eq(CONST_FIELD_ID, id)).firstOrNull()
    }

    open suspend fun findById(id: String): T? {
        return findById(ObjectId(id))
    }

    /**
     * Поиск документа по ID с readConcern MAJORITY.
     * Возвращает данные, подтверждённые большинством узлов реплика-сета.
     * Используйте перед операциями обновления для гарантии консистентности.
     *
     * @param id ID документа
     * @return Найденный документ или null
     */
    suspend fun findByIdForUpdate(id: ObjectId): T? {
        return collection
            .withReadConcern(ReadConcern.MAJORITY)
            .find(Filters.eq(CONST_FIELD_ID, id))
            .firstOrNull()
    }

    /**
     * Возвращает все документы коллекции.
     * Для больших коллекций рекомендуется использовать findByFilterFlow().
     *
     * @return Список всех документов
     */
    suspend fun findAll(): List<T> {
        return collection.find().toList()
    }

    /**
     * Поиск документов с пагинацией.
     *
     * @param limit Максимальное количество документов
     * @param skip Количество пропускаемых документов
     * @return Список найденных документов
     *
     * Пример:
     * ```
     * val firstPage = repo.findLimited(10)      // первые 10
     * val secondPage = repo.findLimited(10, 10) // следующие 10
     * ```
     */
    suspend fun findLimited(limit: Int, skip: Int = 0): List<T> {
        return collection.find()
            .skip(skip)
            .limit(limit)
            .toList()
    }

    /**
     * Поиск с фильтром в виде Flow для реактивной обработки.
     *
     * @param filter DSL-фильтр
     * @return Flow найденных документов
     *
     * Пример:
     * ```
     * repo.findByFilterFlow { UserMongo::age gt 18 }
     *     .collect { user -> println(user) }
     * ```
     */
    fun findByFilterFlow(filter: Bson): Flow<T> {
        return collection.find(filter)
    }

    suspend fun <S> findByField(field: KMutableProperty1<T, S>, value: S): T? {
        return collection.find(Filters.eq(field.name, value)).firstOrNull()
    }

    suspend fun <S> findByFieldList(field: KMutableProperty1<T, S>, value: S): List<T> {
        return collection.find(Filters.eq(field.name, value)).toList()
    }

    suspend fun findByFilter(filter: Bson): List<T> {
        return collection.find(filter).toList()
    }

    /**
     * Поток изменений в коллекции (Change Stream).
     * Позволяет отслеживать все изменения (INSERT, UPDATE, DELETE) в реальном времени.
     * Требует, чтобы MongoDB был запущен как реплика-сет.
     *
     * @return Flow событий изменений
     *
     * Пример:
     * ```
     * repo.watchAll().collect { change ->
     *     when (change.operationType) {
     *         OperationType.INSERT -> println("Добавлен: ${change.fullDocument}")
     *         OperationType.UPDATE -> println("Обновлён: ${change.documentKey}")
     *         OperationType.DELETE -> println("Удалён: ${change.documentKey}")
     *     }
     * }
     * ```
     */
    fun watchAll(): Flow<ChangeStreamDocument<T>> {
        return collection.watch().mapNotNull { it }
    }

    // ==================== UPDATE ОПЕРАЦИИ ====================

    suspend fun update(entity: T, session: ClientSession): UpdateResult {
        val expectedVersion = entity.version
        val newVersion = expectedVersion + 1

        val filter = Filters.and(
            Filters.eq(CONST_FIELD_ID, entity._id),
            Filters.eq(CONST_FIELD_VERSION, expectedVersion)
        )

        val update = Updates.combine(
            Updates.set(CONST_FIELD_VERSION, newVersion),
            Updates.set(CONST_FIELD_UPDATED, LocalDateTime.now()),
            *getUpdateFields(entity).map { (field, value) ->
                Updates.set(field, value)
            }.toTypedArray()
        )

        val result = try {
            collection.updateOne(session, filter, update)
        } catch (e: Exception) {
            throw BaseRepositoryExceptions.funException("update", e.message)
        }

        if (result.matchedCount == 0L) {
            val existing = findById(entity._id)
            if (existing == null) {
                throw BaseRepositoryExceptions.funExceptionFindId("update", entity.getId())
            } else {
                throw BaseRepositoryExceptions.funExceptionRace("update", "current: ${existing.version} need: $expectedVersion")
            }
        }

        if (result.wasAcknowledged() && result.modifiedCount > 0) {
            entity.version = newVersion
        }

        return result
    }

    /**
     * Частичное обновление документа с оптимистичной блокировкой.
     * Обновляет только указанные поля.
     *
     * @param entity Сущность с ID и версией для проверки
     * @param updates Map полей и новых значений
     * @param session Сессия транзакции
     * @return Результат обновления
     *
     * Пример:
     * ```
     * repo.updateFields(
     *     entity = user,
     *     updates = mapOf("name" to "New Name", "age" to 30)
     * )
     * ```
     */
    suspend fun updateFields(
        entity: T,
        updates: Map<String, Any?>,
        session: ClientSession
    ): T {
        //Проверки Мапы новых полей
        validateBeforeUpdate(updates)

        val expectedVersion = entity.version
        val newVersion = expectedVersion + 1

        //Фильтр для поиска нужного объекта по ID и version
        val filter = Filters.and(
            Filters.eq(CONST_FIELD_ID, entity._id),
            Filters.eq(CONST_FIELD_VERSION, expectedVersion)
        )

        //Вручную указываем поля, которые нужно обновить
        val updatesList = mutableListOf<Bson>(
            Updates.set(CONST_FIELD_VERSION, newVersion),
            Updates.set(CONST_FIELD_UPDATED, LocalDateTime.now())
        )

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
            throw BaseRepositoryExceptions.funException("updateFields", e.message)
        }

        if (result == null) {
            throw BaseRepositoryExceptions.funException("updateFields", "Not found object with id ${entity.getId()} after update")
        }

        return result
    }

    // В BaseServiceMongo
    open suspend fun updateFields(
        id: String,
        fields: Map<String, Any?>,
        session: ClientSession
    ): T? {
        val entity = findById(id) ?: throw BaseRepositoryExceptions.funExceptionFindId("updateFields", id)
        return updateFields(entity, fields, session)
    }

    // ==================== DELETE ОПЕРАЦИИ ====================

    /**
     * Удаление документа по ID.
     *
     * @param id ID документа
     * @param session Сессия транзакции
     * @return Результат удаления
     */
    suspend fun deleteById(id: ObjectId, session: ClientSession): DeleteResult {
        val findedObj = findById(id)
        return deleteWithVersion(findedObj, session)
    }

    suspend fun deleteById(id: String, session: ClientSession): DeleteResult {
        return deleteById(ObjectId(id), session)
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
            Filters.eq(CONST_FIELD_VERSION, entity.version)
        )

        val result = try {
            collection.deleteOne(session, filter)
        } catch (e: Exception) {
            throw BaseRepositoryExceptions.funException("deleteWithVersion", e.message)
        }

        validateAfterDelete(entity, session, false)

        if (result.deletedCount == 0L) {
            val existing = findById(entity._id)
            if (existing != null) {
                throw BaseRepositoryExceptions.funExceptionRace("deleteWithVersion", "Entity version mismatch.  Current: ${entity.version} need: ${existing.version}")
            }
        }

        return result
    }

    /**
     * Мягкое удаление документа (устанавливает поле deleted = true).
     *
     * @param id ID документа
     * @param session Сессия транзакции
     * @return Результат обновления
     */
    suspend fun softDelete(id: ObjectId, session: ClientSession): UpdateResult {
        printLog("[SOFT_DELETE::$collectionName] id: $id")

        val findedObj = findById(id)
        if (findedObj == null) {
            throw BaseRepositoryExceptions.funExceptionFindId("softDelete", id.toHexString())
        }

        val filter = Filters.eq(CONST_FIELD_ID, id)
        val update = Updates.set(CONST_FIELD_DELETED, true)

        val result = try {
            collection.updateOne(session, filter, update)
        } catch (e: Exception) {
            throw BaseRepositoryExceptions.funException("softDelete", e.message)
        }
        validateAfterDelete(findedObj, session, true)

        return result
    }

    /**
     * Мягкое удаление документа по сущности.
     */
    suspend fun softDelete(entity: T, session: ClientSession): UpdateResult {
        return softDelete(entity._id, session)
    }

    /**
     * Восстановление мягко удалённого документа (устанавливает deleted = false).
     *
     * @param id ID документа
     * @param session Сессия транзакции
     * @return Результат обновления
     */
    suspend fun restore(id: ObjectId, session: ClientSession): UpdateResult {
        printLog("[RESTORE::$collectionName] id: $id")
        val filter = Filters.eq(CONST_FIELD_ID, id)
        val update = Updates.set(CONST_FIELD_DELETED, false)
        val result = try {
            collection.updateOne(session, filter, update)
        } catch (e: Exception) {
            throw BaseRepositoryExceptions.funException("restore", e.message)
        }
        return result
    }

    /**
     * Восстановление мягко удалённого документа по сущности.
     */
    suspend fun restore(entity: T, session: ClientSession): UpdateResult {
        return restore(entity._id, session)
    }

    // ==================== BULK ОПЕРАЦИИ ====================

    /**
     * Массовое обновление нескольких документов с оптимистичной блокировкой.
     *
     * @param entities Список сущностей для обновления
     * @param session Сессия транзакции
     * @return Результат массовой операции
     */
    suspend fun bulkUpdate(entities: List<T>, session: ClientSession): BulkWriteResult {
        val requests = entities.map { entity ->
            val filter = Filters.and(
                Filters.eq(CONST_FIELD_ID, entity._id),
                Filters.eq(CONST_FIELD_VERSION, entity.version)
            )
            val update = Updates.combine(
                Updates.set(CONST_FIELD_VERSION, entity.version + 1),
                *getUpdateFields(entity).map { (field, value) ->
                    Updates.set(field, value)
                }.toTypedArray()
            )
            UpdateOneModel<T>(filter, update)
        }

        val result = try {
            collection.bulkWrite(session, requests)
        } catch (e: Exception) {
            throw BaseRepositoryExceptions.funException("bulkUpdate", e.message)
        }

        return result
    }

    // ==================== HELPER МЕТОДЫ ====================

    suspend fun exists(id: String, withDeleted: Boolean = false): Boolean {
        return try {
            val objectId = ObjectId(id)
            val filter = Filters.and(
                Filters.eq("_id", objectId),
                Filters.eq("deleted", withDeleted)
            )

            collection.find(filter).firstOrNull() != null
        } catch (e: Exception) {
            throw BaseRepositoryExceptions.funException("exists", e.message)
        }
    }

    suspend fun count(filter: Bson = Filters.empty()): Long {
        return collection.countDocuments(filter)
    }

    suspend fun findPaged(page: Int, pageSize: Int = 20): PagedMongoResponse<T> {
        val items = findLimited(page, pageSize)
        val total = count()
        val pages = if (pageSize > 0) ((total + pageSize - 1) / pageSize).toInt() else 0
        return PagedMongoResponse(items, page, pageSize, total, pages)
    }

    // ==================== ПРИВАТНЫЕ МЕТОДЫ ====================

    /**
     * Получение всех полей сущности для обновления.
     * Исключает служебные поля: id, version, deleted.
     *
     * @param entity Сущность
     * @return Map полей и значений
     */
    private fun getUpdateFields(entity: T): Map<String, Any?> {
        val fields = mutableMapOf<String, Any?>()

        entityClass.memberProperties.forEach { property ->
            val fieldName = property.name
            if (fieldName !in CONST_SYSTEM_FIELDS) {
                val value = property.getter.call(entity)
                fields[fieldName] = value
            }
        }

        return fields
    }

    // ==================== АБСТРАКТНЫЕ МЕТОДЫ ====================

    protected open suspend fun validateBeforeInsert(entity: T) {
        // Базовая реализация — ничего не проверяем
    }

    protected open suspend fun validateBeforeUpdate(changes: Map<String, Any?>) {
        // Базовая реализация — ничего не проверяем
    }

    protected open suspend fun validateAfterInsert(entity: T, session: ClientSession) {

    }

    protected open suspend fun validateAfterDelete(entity: T, session: ClientSession, softDelete: Boolean) {

    }
}
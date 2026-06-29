package base.repository

import base.exception.ExceptionForCode
import base.model.PagedResponse
import com.mongodb.MongoWriteException
import com.mongodb.ReadConcern
import com.mongodb.bulk.BulkWriteResult
import com.mongodb.client.model.*
import com.mongodb.client.model.changestream.ChangeStreamDocument
import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.InsertManyResult
import com.mongodb.client.result.UpdateResult
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import config.MongoFactory
import extensions.CONST_FIELD_DELETED
import extensions.CONST_FIELD_ID
import extensions.CONST_FIELD_UPDATED
import extensions.CONST_FIELD_VERSION
import extensions.CONST_SYSTEM_FIELDS
import extensions.now
import extensions.printLog
import features.VersionedEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import opensavvy.ktmongo.coroutines.JvmMongoCollection
import opensavvy.ktmongo.coroutines.asKtMongo
import opensavvy.ktmongo.dsl.query.FilterQuery
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import server.addons.AppJson
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.starProjectedType

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
abstract class BaseRepositoryMongo<T : VersionedEntity>(
    val collectionName: String,
    private val entityClass: KClass<T>
) {

    // ==================== ПОЛЯ ====================

    /** Коллекция официального драйвера MongoDB для низкоуровневых операций */
    lateinit var collection: MongoCollection<T>

    /** Type-safe обёртка KtMongo для DSL-запросов. Создаётся при каждом обращении */
    private val collectionKT: JvmMongoCollection<T>
        get() = collection.asKtMongo(documentType = entityClass.starProjectedType)

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
                printLog("✅ Уникальный индекс создан: $indexName на полях ${config.fields}")
            } catch (e: MongoWriteException) {
                if (e.code == 85) { // IndexAlreadyExists
                    printLog("ℹ️ Индекс ${config.indexName} уже существует")
                } else {
                    throw e
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
        require(entity.version == 0L) { "Новый entity должен иметь version = 0" }
        validateBeforeInsert(entity)

        return try {
            val result = collection.insertOne(session, entity)
            if (!result.wasAcknowledged() || result.insertedId == null) {
                throw ExceptionForCode("Invalid insertion attempt.", "BRM_INSERT_SET")
            }
            printLog("[ADDED::$collectionName] ${result.insertedId} object: $entity")
            entity._id = result.insertedId!!.asObjectId().value
            entity
        } catch (e: MongoWriteException) {
            if (e.code == 11000) {
                throw ExceptionForCode("Нарушение уникальности при вставке: ${e.message}", "BRM_INSERT_UNIQUE")
            }
            throw e
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
            require(it.version == 0L) { "Новые entity должны иметь version = 0" }
            validateBeforeInsert(it)
        }

        return try {
            val result = collection.insertMany(session, entities)
            printLog("[ADDED_MANY::$collectionName] size: ${result.insertedIds.size}")

            // Присваиваем сгенерированные ID объектам
            entities.forEachIndexed { index, entity ->
                result.insertedIds[index]?.let { bsonValue ->
                    entity._id = bsonValue.asObjectId().value
                    printLog("\t[ADDED_MANY::$collectionName] ${entity._id} object: $entity")
                }
            }

            entities  // ← возвращаем список объектов с присвоенными ID
        } catch (e: MongoWriteException) {
            if (e.code == 11000) {
                throw ExceptionForCode("Нарушение уникальности при массовой вставке", "BRM_INSERTMANY_DUPLICATE")
            }
            throw e
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
     * Поиск всех документов с type-safe DSL фильтром.
     *
     * @param filter DSL-фильтр в стиле KtMongo
     * @return Список найденных документов
     *
     * Пример:
     * ```
     * val users = repo.findAll {
     *     UserMongo::age gt 18
     *     UserMongo::deleted ne true
     * }
     * ```
     */
    suspend fun findAll(filter: FilterQuery<T>.() -> Unit): List<T> {
        return collectionKT.find(filter = filter).toList()
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
    fun findByFilterFlow(filter: FilterQuery<T>.() -> Unit): Flow<T> {
        return collectionKT.find { filter(this) }.asFlow()
    }

    /**
     * Поиск одного документа по type-safe DSL-фильтру.
     *
     * @param filter DSL-фильтр
     * @return Первый найденный документ или null
     *
     * Пример:
     * ```
     * val user = repo.findOneByFilter {
     *     UserMongo::email eq "test@email.com"
     * }
     * ```
     */
    suspend fun findOneByFilter(filter: FilterQuery<T>.() -> Unit): T? {
        return collectionKT.find { filter(this) }.firstOrNull()
    }

    suspend fun findByFilter(filter: FilterQuery<T>.() -> Unit): List<T> {
        return collectionKT.find { filter(this) }.toList()
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

        val result = collection.updateOne(session, filter, update)

        if (result.matchedCount == 0L) {
            val existing = findById(entity._id)
            if (existing == null) {
                throw ExceptionForCode("Документ с id=${entity._id} не найден", "BRM_UPDATE_NOT_FOUND")
            } else {
                throw ExceptionForCode("Race condition при обновлении. Ожидалась версия $expectedVersion, текущая ${existing.version}", "BRM_UPDATE_OPTIMISTIC_LOCK")
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
            throw ExceptionForCode(e.message, "BRM_UPDATEFIELDS_EXCEPTION")
        }

        if (result == null) {
            throw ExceptionForCode("$collection UPDATING error not found", "BRM_UPDATEFIELDS_ERROR")
        }

        return result
    }

    // В BaseServiceMongo
    open suspend fun updateFields(
        id: String,
        fields: Map<String, Any?>,
        session: ClientSession
    ): T? {
        val entity = findById(id) ?: throw ExceptionForCode("Entity not found", "BRM_UPDATEFIELDS_NULL")
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
        printLog("[DELETE::$collectionName] id: $id")
        val result = collection.deleteOne(session, Filters.eq(CONST_FIELD_ID, id))

        // Проверяем, был ли удален документ
        if (result.deletedCount == 0L) {
            throw ExceptionForCode("Документ с id '$id' не найден в коллекции '$collectionName'", "BRM_DELETEID_NOTEXIST")
        }

        printLog("[DELETE::$collectionName] deletedCount: ${result.deletedCount}")
        return result
    }

    suspend fun deleteById(id: String, session: ClientSession): DeleteResult {
        return deleteById(ObjectId(id), session)
    }

    suspend fun deleteById(entity: T, session: ClientSession): DeleteResult {
        return deleteById(entity._id, session)
    }

    suspend fun deleteWithVersion(entity: T?, session: ClientSession): DeleteResult {

        if (entity == null) {
            throw ExceptionForCode("Сущность не найдена", "BRM_DELETEVERSION_NULL")
        }

        val filter = Filters.and(
            Filters.eq(CONST_FIELD_ID, entity._id),
            Filters.eq(CONST_FIELD_VERSION, entity.version)
        )

        val result = collection.deleteOne(session, filter)

        if (result.deletedCount == 0L) {
            val existing = findById(entity._id)
            if (existing != null) {
                throw ExceptionForCode("Документ был изменён перед удалением. Версия ${existing.version} != ${entity.version}", "BRM_DELETEVERSION_NOT_FOUND")
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
        val filter = Filters.eq(CONST_FIELD_ID, id)
        val update = Updates.set(CONST_FIELD_DELETED, true)
        return collection.updateOne(session, filter, update)
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
        return collection.updateOne(session, filter, update)
    }

    /**
     * Восстановление мягко удалённого документа по сущности.
     */
    suspend fun restore(entity: T, session: ClientSession): UpdateResult {
        return restore(entity._id, session)
    }

    /**
     * Удаление всех документов в коллекции (безвозвратно!).
     */
    suspend fun deleteAll(session: ClientSession): DeleteResult {
        printLog("[ALL_DELETE::$collectionName]")
        return collection.deleteMany(session, Filters.empty())
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

        return collection.bulkWrite(session, requests)
    }

    // ==================== HELPER МЕТОДЫ ====================

    suspend fun exists(id: ObjectId, withDeleted: Boolean = false, filter: Bson? = null): Boolean {
        val baseFilter = Filters.ne(CONST_FIELD_DELETED, withDeleted)

        val finalFilter = listOfNotNull(
            Filters.eq(CONST_FIELD_ID, id),
            baseFilter,
            filter
        )

        val combinedFilter = if (finalFilter.size == 1) {
            finalFilter.first()
        } else {
            Filters.and(*finalFilter.toTypedArray())
        }

        return collection.countDocuments(combinedFilter) > 0
    }

    /**
     * Проверка существования документа по сущности.
     */
    suspend fun exists(entity: T, withDeleted: Boolean = false, filter: Bson? = null): Boolean {
        return exists(entity._id, withDeleted, filter)
    }

    suspend fun exists(id: String, withDeleted: Boolean = false, filter: Bson? = null): Boolean {
        return exists(ObjectId(id), withDeleted, filter)
    }

    suspend fun count(filter: FilterQuery<T>.() -> Unit = {}): Long {
        return collectionKT.count { filter(this) }
    }

    /**
     * Проверка существования документа по ID с использованием count.
     * @deprecated Используйте exists()
     */
    @Deprecated("Используйте exists()")
    suspend fun existsById(id: ObjectId): Boolean {
        return exists(id)
    }

    suspend fun findPaged(page: Int, pageSize: Int = 20): PagedResponse<T> {
        val items = findLimited(page, pageSize)
        val total = count()
        val pages = if (pageSize > 0) ((total + pageSize - 1) / pageSize).toInt() else 0
        return PagedResponse(items, page, pageSize, total, pages)
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
}
package mongo_test

import com.mongodb.MongoWriteException
import com.mongodb.ReadConcern
import com.mongodb.bulk.BulkWriteResult
import com.mongodb.client.model.*
import com.mongodb.client.model.changestream.ChangeStreamDocument
import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.InsertManyResult
import com.mongodb.client.result.InsertOneResult
import com.mongodb.client.result.UpdateResult
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import opensavvy.ktmongo.coroutines.JvmMongoCollection
import opensavvy.ktmongo.coroutines.asKtMongo
import opensavvy.ktmongo.dsl.query.FilterQuery
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.starProjectedType

class OptimisticLockException(message: String) : Exception(message)
class DuplicateKeyException(message: String) : Exception(message)
class EntityNotFoundException(message: String) : Exception(message)

data class UniqueIndexConfig(
    val indexName: String,      // имя индекса
    val fields: List<String>,   // список полей для индексации
    val sparse: Boolean = false // для partial уникальности
)

/**
 * Базовый репозиторий с поддержкой:
 * - CRUD операций
 * - Уникальных индексов
 * - Оптимистичной блокировки (через version)
 * - Защиты от race condition
 */
abstract class BaseRepositoryMongo<T : VersionedEntity>(
    private val database: MongoDatabase,
    private val collectionName: String,
    private val entityClass: KClass<T>
) {
    lateinit var collection: MongoCollection<T>
    private val collectionKT: JvmMongoCollection<T>
        get() = collection.asKtMongo(documentType = entityClass.starProjectedType)

    // Кэш для имен полей (для производительности)
    private val idFieldName = "_id"
    private val versionFieldName = "version"

    /**
     * Инициализация репозитория:
     * - Создаёт коллекцию
     * - Настраивает уникальные индексы
     * - Настраивает индекс для версионирования
     */
    suspend fun initialize(uniqueIndexes: List<UniqueIndexConfig> = emptyList()) {
        collection = database.getCollection(collectionName, entityClass.java)

        // Создаём индексы
        setupUniqueIndexes(uniqueIndexes)
        setupVersionIndex()
    }

    /**
     * Создание уникальных индексов
     */
    private suspend fun setupUniqueIndexes(indexes: List<UniqueIndexConfig>) {
        indexes.forEach { config ->
            try {
                val indexOptions = IndexOptions()
                    .unique(true)
                    .name(config.indexName)

                if (config.sparse) {
                    indexOptions.sparse(true)
                }

                val indexModel = IndexModel(
                    Indexes.ascending(*config.fields.toTypedArray()),
                    indexOptions
                )

                val indexName = collection.createIndex(indexModel.keys, indexOptions)
                println("✅ Уникальный индекс создан: $indexName на полях ${config.fields}")
            } catch (e: MongoWriteException) {
                if (e.code == 85) {
                    println("ℹ️ Индекс ${config.indexName} уже существует")
                } else {
                    throw e
                }
            }
        }
    }

    /**
     * Создание индекса для поля version (ускоряет проверки при обновлении)
     */
    private suspend fun setupVersionIndex() {
        try {
            collection.createIndex(Indexes.ascending(versionFieldName))
        } catch (e: Exception) {
            // Индекс уже существует - игнорируем
        }
    }

    // ==================== CREATE ====================

    /**
     * Вставка одного документа
     * @throws DuplicateKeyException если нарушен уникальный индекс
     */
    suspend fun insert(entity: T, session: ClientSession): InsertOneResult {
        require(entity.version == 0) { "Новый entity должен иметь version = 0" }

        validateBeforeInsert(entity)

        return try {
            collection.insertOne(session, entity).apply {
                println("[ADDED::$collectionName] ${this.insertedId} object: $entity")
            }
        } catch (e: MongoWriteException) {
            if (e.code == 11000) {
                throw DuplicateKeyException("Нарушение уникальности при вставке: ${e.message}")
            }
            throw e
        }
    }

    /**
     * Вставка нескольких документов (атомарно)
     */
    suspend fun insertMany(entities: List<T>, session: ClientSession): InsertManyResult {
        entities.forEach {
            require(it.version == 0) { "Новые entity должны иметь version = 0" }
            validateBeforeInsert(it)
        }

        return try {
            collection.insertMany(session, entities).apply {
                println("[ADDED_MANY::$collectionName] size: ${this.insertedIds.size}")
                this.insertedIds.forEach { id ->
                    println("\t[ADDED_MANY::$collectionName] ${id.value} object: ${entities[id.key]}")
                }
            }
        } catch (e: MongoWriteException) {
            if (e.code == 11000) {
                throw DuplicateKeyException("Нарушение уникальности при массовой вставке")
            }
            throw e
        }
    }

    // ==================== READ ====================

    /**
     * Поиск по ID
     *
     * Читает последние данные из одного узла реплика-сета.	Использование: Обычные чтения, где не критична абсолютная свежесть
     */
    suspend fun findById(id: ObjectId): T? {
        return collection.find(Filters.eq(idFieldName, id)).firstOrNull()
    }

    /**
     * Поиск по ID с указанным readConcern
     *
     * Читает данные, которые подтверждены большинством узлов.	Использование: Перед операциями обновления (чтение + запись)
     */
    suspend fun findByIdForUpdate(id: ObjectId): T? {
        // Создаём коллекцию с нужным readConcern для этой операции
        val collectionWithMajorityReadConcern = collection.withReadConcern(ReadConcern.MAJORITY)

        return collectionWithMajorityReadConcern
            .find(Filters.eq(idFieldName, id))
            .firstOrNull()
    }

    /**
     * Поиск всех документов
     */
    suspend fun findAll(): List<T> {
        return collection.find().toList()
    }

    suspend fun findAll(filter: FilterQuery<T>.() -> Unit): List<T> {
        return collectionKT.find(filter = filter).toList()
    }

    /**
     * Поиск с фильтром
     */
    fun findByFilterFlow(filter: FilterQuery<T>.() -> Unit): Flow<T> {
        return collectionKT.find {
            filter(this)
        }.asFlow()
    }

    /**
     * Поиск одного документа по фильтру
     */
    suspend fun findOneByFilter(filter: Bson): T? {
        return collection.find(filter).firstOrNull()
    }

    /**
     * Поток всех изменений в коллекции (для реактивных приложений)
     */
    fun watchAll(): Flow<ChangeStreamDocument<T>> {
        return collection.watch().mapNotNull { it }
    }

    // ==================== UPDATE (с оптимистичной блокировкой) ====================

    /**
     * Обновление документа с проверкой version
     * @throws OptimisticLockException если version не совпал (race condition)
     */
    suspend fun update(entity: T, session: ClientSession): UpdateResult {
        val expectedVersion = entity.version
        val newVersion = expectedVersion + 1

        // Фильтр: ищем по ID и старой версии
        val filter = Filters.and(
            Filters.eq(idFieldName, entity._id),
            Filters.eq(versionFieldName, expectedVersion)
        )

        // Обновление: новые данные + инкремент version
        val update = Updates.combine(
            Updates.set(versionFieldName, newVersion),
            // Обновляем все остальные поля (кроме id и version)
            *getUpdateFields(entity).map { (field, value) ->
                Updates.set(field, value)
            }.toTypedArray()
        )

        val result = collection.updateOne(session, filter, update)

        if (result.matchedCount == 0L) {
            // Проверяем, существует ли документ вообще
            val existing = findById(entity._id)
            if (existing == null) {
                throw EntityNotFoundException("Документ с id=${entity._id} не найден")
            } else {
                throw OptimisticLockException(
                    "Race condition при обновлении документа id=${entity._id}. " +
                            "Ожидалась версия $expectedVersion, текущая версия ${existing.version}"
                )
            }
        }

        // Обновляем версию в переданном объекте
        if (result.wasAcknowledged() && result.modifiedCount > 0) {
            entity.version = newVersion
        }

        return result
    }

    /**
     * Частичное обновление с оптимистичной блокировкой
     */
    suspend fun updateFields(
        entity: T,
        updates: Map<String, Any?>,
        session: ClientSession
    ): UpdateResult {
        val expectedVersion = entity.version
        val id = entity._id

        val newVersion = expectedVersion + 1

        val filter = Filters.and(
            Filters.eq(idFieldName, id),
            Filters.eq(versionFieldName, expectedVersion)
        )

        val updatesList = mutableListOf<Bson>(
            Updates.set(versionFieldName, newVersion)
        )

        updates.forEach { (field, value) ->
            if (field != idFieldName && field != versionFieldName) {
                updatesList.add(Updates.set(field, value))
            }
        }

        val update = Updates.combine(updatesList)
        val result = collection.updateOne(session, filter, update)

        if (result.matchedCount == 0L) {
            val existing = findById(id)
            if (existing == null) {
                throw EntityNotFoundException("Документ с id=$id не найден")
            } else {
                throw OptimisticLockException(
                    "Race condition: ожидалась версия $expectedVersion, текущая ${existing.version}"
                )
            }
        }

        return result
    }

    /**
     * Атомарное обновление через findOneAndUpdate (возвращает обновлённый документ)
     */
    suspend fun updateAndReturn(entity: T, session: ClientSession): T? {
        val expectedVersion = entity.version
        val newVersion = expectedVersion + 1

        val filter = Filters.and(
            Filters.eq(idFieldName, entity._id),
            Filters.eq(versionFieldName, expectedVersion)
        )

        val update = Updates.combine(
            Updates.set(versionFieldName, newVersion),
            *getUpdateFields(entity).map { (field, value) ->
                Updates.set(field, value)
            }.toTypedArray()
        )

        val options = FindOneAndUpdateOptions()
            .returnDocument(ReturnDocument.AFTER)

        return try {
            val updated = collection.findOneAndUpdate(session, filter, update, options)
            updated?.also { entity.version = newVersion }
        } catch (e: Exception) {
            if (findById(entity._id) == null) {
                throw EntityNotFoundException("Документ не найден")
            } else {
                throw OptimisticLockException("Race condition при обновлении")
            }
        }
    }

    // ==================== DELETE ====================

    /**
     * Удаление по ID без проверки версии
     */
    suspend fun deleteById(id: ObjectId, session: ClientSession): DeleteResult {
        return collection.deleteOne(session, Filters.eq(idFieldName, id))
    }

    /**
     * Удаление с проверкой версии (безопасное удаление)
     */
    suspend fun deleteWithVersion(entity: T, session: ClientSession): DeleteResult {
        val filter = Filters.and(
            Filters.eq(idFieldName, entity._id),
            Filters.eq(versionFieldName, entity.version)
        )

        val result = collection.deleteOne(session, filter)

        if (result.deletedCount == 0L) {
            val existing = findById(entity._id)
            if (existing != null) {
                throw OptimisticLockException(
                    "Документ был изменён перед удалением. Версия ${existing.version} != ${entity.version}"
                )
            }
            // Документ уже был удалён — считаем успехом
        }

        return result
    }

    /**
     * Мягкое удаление (устанавливает флаг deleted)
     */
    suspend fun softDelete(id: ObjectId, session: ClientSession): UpdateResult {
        val filter = Filters.eq(idFieldName, id)
        val update = Updates.set("deleted", true)
        return collection.updateOne(session, filter, update)
    }

    /**
     * Удаление всех документов (осторожно!)
     */
    suspend fun deleteAll(session: ClientSession): DeleteResult {
        return collection.deleteMany(session, Filters.empty())
    }

    // ==================== BULK OPERATIONS ====================

    /**
     * Пакетная операция с использованием bulkWrite
     */
    suspend fun bulkUpdate(entities: List<T>, session: ClientSession): BulkWriteResult {
        val requests = entities.map { entity ->
            val filter = Filters.and(
                Filters.eq(idFieldName, entity._id),
                Filters.eq(versionFieldName, entity.version)
            )
            val update = Updates.combine(
                Updates.set(versionFieldName, entity.version + 1),
                *getUpdateFields(entity).map { (field, value) ->
                    Updates.set(field, value)
                }.toTypedArray()
            )
            UpdateOneModel<T>(filter, update)
        }

        return collection.bulkWrite(session, requests)
    }

    // ==================== HELPER METHODS ====================

    /**
     * Проверка существования документа
     */
    suspend fun exists(id: ObjectId): Boolean {
        return collection.countDocuments(Filters.eq(idFieldName, id)) > 0
    }

    /**
     * Количество документов в коллекции
     */
    suspend fun count(): Long {
        return collection.countDocuments()
    }

    /**
     * Получение всех полей, кроме id и version, для обновления
     */
    private fun getUpdateFields(entity: T): Map<String, Any?> {
        val fields = mutableMapOf<String, Any?>()

        entityClass.memberProperties.forEach { property ->
            val fieldName = property.name
            if (fieldName != "id" && fieldName != "version") {
                val value = property.getter.call(entity)
                fields[fieldName] = value
            }
        }

        return fields
    }

    /**
     * Метод валидации перед вставкой.
     * Переопределяется в конкретных репозиториях.
     *
     * @param entity Сущность для проверки
     * @throws IllegalArgumentException если валидация не пройдена
     */
    protected open suspend fun validateBeforeInsert(entity: T) {
        // Базовая реализация — ничего не проверяем
        // Конкретные репозитории переопределяют этот метод
    }
}
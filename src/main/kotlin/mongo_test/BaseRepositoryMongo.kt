package mongo_test

import com.mongodb.MongoWriteException
import com.mongodb.ReadConcern
import com.mongodb.bulk.BulkWriteResult
import com.mongodb.client.model.*
import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.InsertManyResult
import com.mongodb.client.result.InsertOneResult
import com.mongodb.client.result.UpdateResult
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
    lateinit var collectionKT: JvmMongoCollection<T>

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
        collectionKT = collection.asKtMongo(documentType = entityClass.starProjectedType)

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
    suspend fun insert(entity: T): InsertOneResult {
        require(entity.version == 0) { "Новый entity должен иметь version = 0" }

        validateBeforeInsert(entity)

        return try {
            collection.insertOne(entity).apply {
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
    suspend fun insertMany(entities: List<T>): InsertManyResult {
        entities.forEach {
            require(it.version == 0) { "Новые entity должны иметь version = 0" }
            validateBeforeInsert(it)
        }

        return try {
            collection.insertMany(entities).apply {
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

    fun findAllFlow(): Flow<T> {
        return collectionKT.find().asFlow()
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
    fun watchAll(): Flow<T> {
        return collection.watch().mapNotNull { it.fullDocument }
    }

    // ==================== UPDATE (с оптимистичной блокировкой) ====================

    /**
     * Обновление документа с проверкой version
     * @throws OptimisticLockException если version не совпал (race condition)
     */
    suspend fun update(entity: T): UpdateResult {
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

        val result = collection.updateOne(filter, update)

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
        id: ObjectId,
        expectedVersion: Int,
        updates: Map<String, Any?>
    ): UpdateResult {
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
        val result = collection.updateOne(filter, update)

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
     * Обновление или вставка (upsert) с оптимистичной блокировкой
     */
    suspend fun upsert(entity: T): UpdateResult {
        val filter = Filters.eq(idFieldName, entity._id)

        val update = Updates.combine(
            Updates.setOnInsert(versionFieldName, 0),
            *getUpdateFields(entity).map { (field, value) ->
                Updates.set(field, value)
            }.toTypedArray()
        )

        val options = UpdateOptions().upsert(true)
        return collection.updateOne(filter, update, options)
    }

    /**
     * Атомарное обновление через findOneAndUpdate (возвращает обновлённый документ)
     */
    suspend fun updateAndReturn(entity: T): T? {
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
            val updated = collection.findOneAndUpdate(filter, update, options)
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
    suspend fun deleteById(id: ObjectId): DeleteResult {
        return collection.deleteOne(Filters.eq(idFieldName, id))
    }

    /**
     * Удаление с проверкой версии (безопасное удаление)
     */
    suspend fun deleteWithVersion(entity: T): DeleteResult {
        val filter = Filters.and(
            Filters.eq(idFieldName, entity._id),
            Filters.eq(versionFieldName, entity.version)
        )

        val result = collection.deleteOne(filter)

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
    suspend fun softDelete(id: ObjectId): UpdateResult {
        val filter = Filters.eq(idFieldName, id)
        val update = Updates.set("deleted", true)
        return collection.updateOne(filter, update)
    }

    /**
     * Удаление всех документов (осторожно!)
     */
    suspend fun deleteAll(): DeleteResult {
        return collection.deleteMany(Filters.empty())
    }

    // ==================== BULK OPERATIONS ====================

    /**
     * Массовое обновление с optimistic locking
     */
    suspend fun updateMany(entities: List<T>): List<UpdateResult> {
        val results = mutableListOf<UpdateResult>()
        for (entity in entities) {
            results.add(update(entity))
        }
        return results
    }

    /**
     * Пакетная операция с использованием bulkWrite
     */
    suspend fun bulkUpdate(entities: List<T>): BulkWriteResult {
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

        return collection.bulkWrite(requests)
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
package base.repository

import CONST_FIELD_ID
import CONST_FIELD_UPDATED
import CONST_FIELD_VERSION
import CONST_PAGE_SIZE_DEFAULT
import CONST_SYSTEM_FIELDS
import base.entity.StockEntity
import base.entity.VersionedEntity
import base.exception.BaseException
import base.exception.BaseRepositoryExceptions
import base.route.PagedMongoResponse
import com.mongodb.MongoBulkWriteException
import com.mongodb.MongoCommandException
import com.mongodb.MongoWriteException
import com.mongodb.client.model.Filters
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Projections
import com.mongodb.client.model.ReturnDocument
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoCollection
import config.MongoFactory
import config.afterCommit
import extensions.now
import extensions.printLog
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.LocalDateTime
import org.bson.BsonDocument
import org.bson.BsonDocumentWriter
import org.bson.codecs.EncoderContext
import org.bson.conversions.Bson
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/**
 * Индекс коллекции: поля по возрастанию, уникальность, разреженность и имя.
 * Репозиторий объявляет свои в [BaseRepository.indexes], создаёт их [BaseRepository.ensureIndexes].
 */
data class IndexSpec(
    val fields: List<String>,
    val unique: Boolean = false,
    val sparse: Boolean = false,
    val name: String? = null,
) {
    companion object {
        /** Обычный индекс по [fields] - для полей-ссылок, по которым идут выборки. */
        fun on(vararg fields: String) = IndexSpec(fields.toList())

        /** Уникальный индекс с явным именем: так он узнаётся при повторном создании. */
        fun unique(name: String, vararg fields: String, sparse: Boolean = false) = IndexSpec(fields.toList(), unique = true, sparse = sparse, name = name)
    }
}

/** Код MongoDB «дубликат уникального ключа». */
private const val DUPLICATE_KEY = 11000

/** Коды MongoDB «такой индекс уже есть» - с другим именем или другими опциями. */
private val INDEX_CONFLICTS = setOf(85, 86)

/**
 * Репозиторий коллекции MongoDB: CRUD с оптимистичной блокировкой по `version`,
 * скрытие мягко удалённых документов, синхронизация справочного кеша после коммита
 * и хуки валидации для наследников.
 *
 * Имя коллекции - простое имя класса сущности.
 */
abstract class BaseRepository<T : StockEntity>(private val entityClass: KClass<T>) {

    private val collectionName = entityClass.simpleName!!

    /** Коллекция драйвера: для точечных запросов наследников, которым не хватает общих. */
    val collection: MongoCollection<T> = MongoFactory.getDatabase().getCollection(collectionName, entityClass.java)

    /**
     * Фильтр обычного чтения, см. [SoftDelete.readFilter]. Все выборки репозитория идут через
     * него; наследник, который лезет в [collection] напрямую, обязан применить его сам.
     */
    protected fun readFilter(filter: Bson? = null, includeDeleted: Boolean = false): Bson =
        SoftDelete.readFilter(filter, includeDeleted)

    /**
     * Кеш коллекции, если она справочная: правки доезжают в него после коммита
     * транзакции (с 0.49.0), поэтому откат не оставляет кеш впереди базы.
     */
    protected open val cache: EntityCache<T>? get() = null

    /**
     * Поля, которыми владеет база, а не объект в памяти: полная запись [update] их не трогает.
     * Так счётчик, который двигает `$inc` из другого репозитория, не откатывается устаревшей копией.
     */
    protected open val managedFields: Set<String> get() = emptySet()

    // ==================== ИНДЕКСЫ ====================

    /** Индексы коллекции; создаются при старте [ensureIndexes], а не на первом обращении. */
    protected open val indexes: List<IndexSpec> get() = emptyList()

    /**
     * Создаёт объявленные индексы. Вызывается при старте до первой транзакции: создание индекса
     * меняет каталог MongoDB, и открытая транзакция упала бы с WriteConflict. Одиночный индекс по `version` прежних версий снимается: `_id` и так
     * находит документ, а лишний индекс только замедлял каждую запись.
     */
    suspend fun ensureIndexes() {
        indexes.forEach { spec ->
            val options = IndexOptions().unique(spec.unique).sparse(spec.sparse).apply { spec.name?.let(::name) }
            try {
                collection.createIndex(Indexes.ascending(spec.fields), options)
            } catch (e: MongoCommandException) {
                // Индекс с теми же полями уже есть под другим именем - оставляем его. Прочее не
                // роняет старт: без индекса сервер медленнее, но работает, а причина - в логе.
                if (e.code !in INDEX_CONFLICTS) printLog("❌ [$collectionName] index ${spec.fields} not created: ${e.errorMessage}", true)
            }
        }
        runCatching { collection.dropIndex(Indexes.ascending(CONST_FIELD_VERSION)) }
    }

    // ==================== CREATE ====================

    /**
     * Вставляет новый документ (версия 0).
     *
     * @param validation false - без хуков [validateBeforeInsert] и [validateAfterInsert]
     */
    suspend fun insert(entity: T, session: ClientSession, validation: Boolean = true): T {
        requireNew(entity, "insert")
        if (validation) validateBeforeInsert(entity, session)
        val result = writing("insert") { collection.insertOne(session, entity) }
        val insertedId = result.insertedId
        if (!result.wasAcknowledged() || insertedId == null) throw BaseRepositoryExceptions.funExceptionInsertInvalid("insert")
        entity._id = insertedId.asString().value
        printLog("[ADDED::$collectionName] ${entity._id}")
        if (validation) validateAfterInsert(entity, session)
        return entity
    }

    /** Вставляет документы одной командой; атомарность даёт транзакция [session]. */
    suspend fun insertMany(entities: List<T>, session: ClientSession): List<T> {
        if (entities.isEmpty()) return emptyList()
        entities.forEach {
            requireNew(it, "insertMany")
            validateBeforeInsert(it, session)
        }
        val result = writing("insertMany") { collection.insertMany(session, entities) }
        printLog("[ADDED_MANY::$collectionName] size: ${result.insertedIds.size}")
        entities.forEachIndexed { index, entity ->
            result.insertedIds[index]?.let { id ->
                entity._id = id.asString().value
                validateAfterInsert(entity, session)
            }
        }
        return entities
    }

    private fun requireNew(entity: T, method: String) {
        if (entity is VersionedEntity && entity.version != 0L)
            throw BaseRepositoryExceptions.funExceptionInsertVersion(method, entity.version.toString())
    }

    // ==================== READ ====================

    /*
     * Все чтения скрывают мягко удалённые документы; includeDeleted - единственный способ
     * достать удалённое, например для проверки занятости уникального поля.
     */

    suspend fun findById(id: String, includeDeleted: Boolean = false): T? =
        collection.find(readFilter(byId(id), includeDeleted)).firstOrNull()

    suspend fun findById(id: String, session: ClientSession, includeDeleted: Boolean = false): T? =
        collection.find(session, readFilter(byId(id), includeDeleted)).firstOrNull()

    /** Документ по id или ошибка из [missing]: общий вид «найди или откажи» для всех репозиториев. */
    suspend inline fun requireById(id: String, missing: (String) -> Throwable): T = findById(id) ?: throw missing(id)

    /** Есть ли документ: читается только `_id`, без самого документа. */
    suspend fun exists(id: String, includeDeleted: Boolean = false): Boolean =
        collection.find(readFilter(byId(id), includeDeleted)).projection(Projections.include(CONST_FIELD_ID)).limit(1).firstOrNull() != null

    suspend fun findAll(includeDeleted: Boolean = false): List<T> =
        collection.find(readFilter(includeDeleted = includeDeleted)).toList()

    suspend fun findAll(session: ClientSession, includeDeleted: Boolean = false): List<T> =
        collection.find(session, readFilter(includeDeleted = includeDeleted)).toList()

    /** Первый документ, у которого поле [field] равно [value]. */
    suspend fun <S> findByField(field: KProperty1<T, S>, value: S, includeDeleted: Boolean = false): T? =
        collection.find(readFilter(Filters.eq(field.name, value), includeDeleted)).firstOrNull()

    suspend fun <S> findByField(field: KProperty1<T, S>, value: S, session: ClientSession, includeDeleted: Boolean = false): T? =
        collection.find(session, readFilter(Filters.eq(field.name, value), includeDeleted)).firstOrNull()

    suspend fun findByFilter(filter: Bson, includeDeleted: Boolean = false): List<T> =
        collection.find(readFilter(filter, includeDeleted)).toList()

    suspend fun count(filter: Bson = Filters.empty(), includeDeleted: Boolean = false): Long =
        collection.countDocuments(readFilter(filter, includeDeleted))

    suspend fun count(session: ClientSession, filter: Bson = Filters.empty(), includeDeleted: Boolean = false): Long =
        collection.countDocuments(session, readFilter(filter, includeDeleted))

    /**
     * Страница документов по фильтру: страницы с нуля, размер приводится [PageRequest].
     * Порядок задан явно (по умолчанию `_id`), иначе документ мог бы попасть на две страницы.
     */
    suspend fun findPaged(
        filter: Bson,
        page: Int,
        pageSize: Int = CONST_PAGE_SIZE_DEFAULT,
        sort: Bson = Sorts.ascending(CONST_FIELD_ID),
        includeDeleted: Boolean = false
    ): PagedMongoResponse<T> {
        val request = PageRequest.of(page, pageSize)
        val query = readFilter(filter, includeDeleted)
        val items = collection.find(query).sort(sort).skip(request.skip).limit(request.size).toList()
        val totalItems = collection.countDocuments(query)
        return PagedMongoResponse(items, request.page, totalItems, request.totalPages(totalItems))
    }

    suspend fun findPaged(page: Int, pageSize: Int = CONST_PAGE_SIZE_DEFAULT, includeDeleted: Boolean = false): PagedMongoResponse<T> =
        findPaged(Filters.empty(), page, pageSize, includeDeleted = includeDeleted)

    // ==================== UPDATE ====================

    /**
     * Полная запись документа с оптимистичной блокировкой: запись проходит, только если версия
     * в базе та же, что в объекте; после неё версия объекта растёт.
     *
     * @throws BaseRepositoryExceptions.BaseRepositoryException документа нет или его версия ушла вперёд
     */
    suspend fun update(entity: T, session: ClientSession): UpdateResult {
        val fields = encode(entity).map { (field, value) -> Updates.set(field, value) }
        val result = writing("update") { collection.updateOne(session, identity(entity), Updates.combine(versionBump(entity) + fields)) }
        if (result.matchedCount == 0L) missed("update", entity)
        if (entity is VersionedEntity && result.modifiedCount > 0) entity.version += 1
        validateAfterUpdate(entity, session)
        return result
    }

    /**
     * Частичная запись полей [updates] с оптимистичной блокировкой; отвечает документом после
     * записи. Объект [entity] в памяти не меняется.
     */
    suspend fun updateFields(entity: T, updates: Map<String, Any?>, session: ClientSession): T {
        validateBeforeUpdate(updates)
        val fields = updates.filterKeys { it != CONST_FIELD_ID && it != CONST_FIELD_VERSION }.map { (field, value) -> Updates.set(field, value) }
        printLog("[UPDATE::$collectionName] id: ${entity._id}")
        val options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        val result = writing("updateFields") {
            collection.findOneAndUpdate(session, identity(entity), Updates.combine(versionBump(entity) + fields), options)
        } ?: throw BaseRepositoryExceptions.funException("updateFields", "Not found object with id ${entity._id} after update")
        validateAfterUpdate(result, session)
        return result
    }

    /** То же по id: документ читается и пишется с проверкой его текущей версии. */
    suspend fun updateFields(id: String, fields: Map<String, Any?>, session: ClientSession): T? =
        updateFields(requireById(id) { BaseRepositoryExceptions.funExceptionFindId("updateFields", it) }, fields, session)

    // ==================== DELETE ====================

    /** Жёсткое удаление по id с проверкой версии. */
    suspend fun deleteById(id: String, session: ClientSession): DeleteResult = delete(findById(id), session)

    /** Жёсткое удаление прочитанного документа с проверкой версии. */
    suspend fun deleteById(entity: T, session: ClientSession): DeleteResult = delete(entity, session)

    private suspend fun delete(entity: T?, session: ClientSession): DeleteResult {
        printLog("[DELETE::$collectionName] id: ${entity?._id}")
        if (entity == null) throw BaseRepositoryExceptions.funExceptionEntityNull("delete")
        val result = writing("delete") { collection.deleteOne(session, identity(entity)) }
        validateAfterDelete(entity, session)
        if (result.deletedCount == 0L && exists(entity._id)) missed("delete", entity)
        return result
    }

    /**
     * Удаляет коллекцию целиком вместе с индексами. Меняет каталог MongoDB, поэтому внутри
     * транзакции нельзя - там нужен [deleteAll] с сессией.
     */
    suspend fun deleteAll() {
        printLog("[DELETE_All::$collectionName]")
        collection.drop()
    }

    /** Удаляет все документы в транзакции, не трогая коллекцию и индексы. */
    suspend fun deleteAll(session: ClientSession): Long {
        val deleted = collection.deleteMany(session, Filters.empty()).deletedCount
        printLog("[DELETE_All::$collectionName] deleted: $deleted")
        return deleted
    }

    // ==================== ОБЩЕЕ ====================

    private fun byId(id: String): Bson = Filters.eq(CONST_FIELD_ID, id)

    /** Документ по id и, у версионируемой записи, по версии, которую держит объект в памяти. */
    private fun identity(entity: T): Bson =
        if (entity is VersionedEntity) Filters.and(byId(entity._id), Filters.eq(CONST_FIELD_VERSION, entity.version))
        else byId(entity._id)

    /** Служебные поля следующей версии: номер и время правки. */
    private fun versionBump(entity: T): List<Bson> =
        if (entity is VersionedEntity) listOf(Updates.set(CONST_FIELD_VERSION, entity.version + 1), Updates.set(CONST_FIELD_UPDATED, LocalDateTime.now()))
        else emptyList()

    /** Запись по [identity] ничего не нашла: документа нет вовсе или его версия ушла вперёд. */
    private suspend fun missed(method: String, entity: T): Nothing {
        val existing = findById(entity._id) ?: throw BaseRepositoryExceptions.funExceptionFindId(method, entity._id)
        throw BaseRepositoryExceptions.funExceptionRace(method, "current: ${existing.versionOrZero()} need: ${entity.versionOrZero()}")
    }

    private fun StockEntity.versionOrZero(): Long = (this as? VersionedEntity)?.version ?: 0L

    /**
     * Ошибки драйвера при записи - в ошибки репозитория: дубликат уникального ключа - это гонка,
     * всё прочее - общая ошибка с именем операции. Бизнес-ошибки проходят как есть.
     */
    private inline fun <R> writing(method: String, block: () -> R): R = try {
        block()
    } catch (e: BaseException) {
        throw e
    } catch (e: MongoWriteException) {
        throw if (e.code == DUPLICATE_KEY) BaseRepositoryExceptions.funExceptionRace(method, e.message) else BaseRepositoryExceptions.funException(method, e.message)
    } catch (e: MongoBulkWriteException) {
        throw BaseRepositoryExceptions.funException(method, e.writeErrors.firstOrNull()?.message ?: e.message)
    } catch (e: Exception) {
        throw BaseRepositoryExceptions.funException(method, e.message)
    }

    /**
     * Поля сущности для полной записи: документ кодеком коллекции - тем же, которым её пишет
     * insert, - минус системные и управляемые базой поля.
     */
    private fun encode(entity: T): Map<String, Any?> {
        val document = BsonDocument()
        collection.codecRegistry.get(entityClass.java).encode(BsonDocumentWriter(document), entity, EncoderContext.builder().build())
        return document.filterKeys { it !in CONST_SYSTEM_FIELDS && it !in managedFields }
    }

    // ==================== ХУКИ ====================

    /** Проверка перед вставкой; вызывается для каждой сущности [insert] и [insertMany]. */
    protected open suspend fun validateBeforeInsert(entity: T, session: ClientSession) = Unit

    /** Проверка изменений перед частичной записью [updateFields]. */
    protected open suspend fun validateBeforeUpdate(changes: Map<String, Any?>) = Unit

    protected open suspend fun validateAfterUpdate(entity: T, session: ClientSession) {
        cache?.let { afterCommit { it.updateItem(entity) } }
    }

    protected open suspend fun validateAfterInsert(entity: T, session: ClientSession) {
        cache?.let { afterCommit { it.addItem(entity) } }
    }

    protected open suspend fun validateAfterDelete(entity: T, session: ClientSession) {
        cache?.let { afterCommit { it.removeItem(entity) } }
    }
}

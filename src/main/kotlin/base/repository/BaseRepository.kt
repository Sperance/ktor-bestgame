package base.repository

import CONST_FIELD_ID
import CONST_FIELD_VERSION
import CONST_PAGE_SIZE_DEFAULT
import CONST_PAGE_SIZE_MAX
import base.entity.StockEntity
import base.entity.TrackedEntity
import base.entity.VersionedEntity
import base.exception.BaseRepositoryExceptions
import base.route.CursorPage
import base.route.PagedMongoResponse
import com.mongodb.MongoCommandException
import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Projections
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import com.mongodb.client.result.DeleteResult
import com.mongodb.client.result.UpdateResult
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.mongodb.kotlin.client.coroutine.MongoCollection
import config.MongoFactory
import config.TransactionHooks
import config.afterCommit
import extensions.printLog
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import org.bson.BsonDocument
import org.bson.conversions.Bson
import server.addons.CommandKey
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
    /** Какие документы попадают в индекс; у разреженного индекса пустая строка всё равно считается значением. */
    val partial: Bson? = null,
) {
    companion object {
        /** Обычный индекс по [fields] - для полей-ссылок, по которым идут выборки. */
        fun on(vararg fields: String) = IndexSpec(fields.toList())

        /** Уникальный индекс с явным именем: так он узнаётся при повторном создании. */
        fun unique(name: String, vararg fields: String, sparse: Boolean = false) = IndexSpec(fields.toList(), unique = true, sparse = sparse, name = name)

        /** Уникальность только среди заполненных значений: пустая строка повторяться может. */
        fun uniqueFilled(name: String, field: String) = IndexSpec(listOf(field), unique = true, name = name, partial = Filters.gt(field, ""))
    }
}

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

    /** Документ сущности: кодек, память прочитанного и разница для записи. */
    private val entityCodec = EntityCodec(entityClass, { collection.codecRegistry }, { managedFields }, { stored(it) })

    /**
     * Фильтр обычного чтения, см. [SoftDelete.readFilter]. Все выборки репозитория идут через
     * него; наследник, который лезет в [collection] напрямую, обязан применить его сам.
     */
    protected fun readFilter(filter: Bson? = null, includeDeleted: Boolean = false): Bson = SoftDelete.readFilter(filter, includeDeleted)

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
     * меняет каталог MongoDB, и открытая транзакция упала бы с WriteConflict.
     */
    suspend fun ensureIndexes() {
        indexes.forEach { spec ->
            val options = IndexOptions().unique(spec.unique).sparse(spec.sparse)
                .apply { spec.name?.let(::name) }
                .apply { spec.partial?.let(::partialFilterExpression) }
            try {
                collection.createIndex(Indexes.ascending(spec.fields), options)
            } catch (e: MongoCommandException) {
                // Индекс с теми же полями уже есть под другим именем - оставляем его. Прочее не
                // роняет старт: без индекса сервер медленнее, но работает, а причина - в логе.
                if (e.code !in INDEX_CONFLICTS) printLog("❌ [$collectionName] index ${spec.fields} not created: ${e.errorMessage}", true)
            }
        }
    }

    // ==================== CREATE ====================

    /**
     * Вставляет новый документ (версия 0).
     *
     * @param validation false - без хуков [validateBeforeInsert] и [validateAfterInsert]
     */
    suspend fun insert(source: T, session: ClientSession, validation: Boolean = true): T {
        requireNew(source, "insert")
        val entity = if (validation) admit(source) else source
        if (validation) validateBeforeInsert(entity, session)
        val result = writing(collectionName, "insert") { collection.insertOne(session, entity) }
        val insertedId = result.insertedId
        if (!result.wasAcknowledged() || insertedId == null) throw BaseRepositoryExceptions.funExceptionInsertInvalid("insert")
        entity._id = insertedId.asString().value
        printLog("[ADDED::$collectionName] ${entity._id}")
        if (validation) validateAfterInsert(entity, session)
        return entity
    }

    /** Вставляет документы одной командой; атомарность даёт транзакция [session]. */
    suspend fun insertMany(sources: List<T>, session: ClientSession): List<T> {
        if (sources.isEmpty()) return emptyList()
        val entities = sources.map {
            requireNew(it, "insertMany")
            admit(it).also { entity -> validateBeforeInsert(entity, session) }
        }
        val result = writing(collectionName, "insertMany") { collection.insertMany(session, entities) }
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
        if (entity is VersionedEntity && entity.version != 0L) {
            throw BaseRepositoryExceptions.funExceptionInsertVersion(method, entity.version.toString())
        }
    }

    // ==================== READ ====================

    /*
     * Все чтения скрывают мягко удалённые документы; includeDeleted - единственный способ
     * достать удалённое, например для проверки занятости уникального поля.
     */

    suspend fun findById(id: String, includeDeleted: Boolean = false): T? = collection.find(readFilter(Versioning.byId(id), includeDeleted)).firstOrNull()?.tracked()

    suspend fun findById(id: String, session: ClientSession, includeDeleted: Boolean = false): T? = collection.find(session, readFilter(Versioning.byId(id), includeDeleted)).firstOrNull()?.tracked()

    /** Документ по id или ошибка из [missing]: общий вид «найди или откажи» для всех репозиториев. */
    suspend inline fun requireById(id: String, missing: (String) -> Throwable): T = findById(id) ?: throw missing(id)

    /** Есть ли документ: читается только `_id`, без самого документа. */
    suspend fun exists(id: String, includeDeleted: Boolean = false): Boolean = // Без документа сущности: проекция из одного `_id` не соберётся в класс с обязательными полями
        collection.withDocumentClass<org.bson.Document>().find(readFilter(Versioning.byId(id), includeDeleted))
            .projection(Projections.include(CONST_FIELD_ID)).limit(1).firstOrNull() != null

    suspend fun findAll(includeDeleted: Boolean = false): List<T> = collection.find(readFilter(includeDeleted = includeDeleted)).toList().onEach { it.tracked() }

    suspend fun findAll(session: ClientSession, includeDeleted: Boolean = false): List<T> = collection.find(session, readFilter(includeDeleted = includeDeleted)).toList().onEach { it.tracked() }

    /** Первый документ, у которого поле [field] равно [value]. */
    suspend fun <S> findByField(field: KProperty1<T, S>, value: S, includeDeleted: Boolean = false): T? = collection.find(readFilter(Filters.eq(field.name, value), includeDeleted)).firstOrNull()?.tracked()

    suspend fun <S> findByField(field: KProperty1<T, S>, value: S, session: ClientSession, includeDeleted: Boolean = false): T? = collection.find(session, readFilter(Filters.eq(field.name, value), includeDeleted)).firstOrNull()?.tracked()

    suspend fun findByFilter(filter: Bson, includeDeleted: Boolean = false): List<T> = collection.find(readFilter(filter, includeDeleted)).toList().onEach { it.tracked() }

    suspend fun count(filter: Bson = Filters.empty(), includeDeleted: Boolean = false): Long = collection.countDocuments(readFilter(filter, includeDeleted))

    suspend fun count(session: ClientSession, filter: Bson = Filters.empty(), includeDeleted: Boolean = false): Long = collection.countDocuments(session, readFilter(filter, includeDeleted))

    /**
     * Страница документов по фильтру: страницы с нуля, размер приводится [PageRequest].
     * Порядок задан явно (по умолчанию `_id`), иначе документ мог бы попасть на две страницы.
     */
    suspend fun findPaged(
        filter: Bson,
        page: Int,
        pageSize: Int = CONST_PAGE_SIZE_DEFAULT,
        sort: Bson = Sorts.ascending(CONST_FIELD_ID),
        includeDeleted: Boolean = false,
    ): PagedMongoResponse<T> {
        val request = PageRequest.of(page, pageSize)
        val query = readFilter(filter, includeDeleted)
        val items = collection.find(query).sort(sort).skip(request.skip).limit(request.size).toList().onEach { it.tracked() }
        val totalItems = collection.countDocuments(query)
        return PagedMongoResponse(items, request.page, totalItems, request.totalPages(totalItems))
    }

    /** Страница по курсору: документы с `_id` больше [after] (пустой - с начала) в порядке `_id`, не больше [size]. */
    suspend fun findAfter(filter: Bson, after: String?, size: Int = CONST_PAGE_SIZE_DEFAULT, includeDeleted: Boolean = false): CursorPage<T> {
        val limit = size.coerceIn(1, CONST_PAGE_SIZE_MAX)
        val query = readFilter(filter, includeDeleted)
        val page = after?.takeIf { it.isNotBlank() }?.let { Filters.and(query, Filters.gt(CONST_FIELD_ID, it)) } ?: query
        val found = collection.find(page).sort(Sorts.ascending(CONST_FIELD_ID)).limit(limit + 1).toList()
        val items = found.take(limit).onEach { it.tracked() }
        return CursorPage(items, items.lastOrNull()?._id?.takeIf { found.size > limit }, collection.countDocuments(query))
    }

    suspend fun findPaged(page: Int, pageSize: Int = CONST_PAGE_SIZE_DEFAULT, includeDeleted: Boolean = false): PagedMongoResponse<T> = findPaged(Filters.empty(), page, pageSize, includeDeleted = includeDeleted)

    // ==================== UPDATE ====================

    /**
     * Полная запись документа с оптимистичной блокировкой: запись проходит, только если версия
     * в базе та же, что в объекте; после неё версия объекта растёт. Сущность [TrackedEntity] пишет
     * только поля, изменившиеся с чтения: версия всё равно проверяется, так что чужая правка между
     * чтением и записью - гонка, а не потерянное поле.
     *
     * @throws BaseRepositoryExceptions.BaseRepositoryException документа нет или его версия ушла вперёд
     */
    suspend fun update(entity: T, session: ClientSession): UpdateResult {
        settle(entity)
        val encoded = entityCodec.encode(entity)
        val result = writing(collectionName, "update") { collection.updateOne(session, Versioning.identity(entity), Updates.combine(Versioning.bump(entity) + entityCodec.changes(entity, encoded))) }
        if (result.matchedCount == 0L) missed("update", entity)
        if (entity is VersionedEntity && result.modifiedCount > 0) entity.version += 1
        // Память документа сдвигается только с коммитом: откат оставляет её той, что лежит в базе
        if (entity is TrackedEntity) afterCommit { entity.loaded = encoded }
        validateAfterUpdate(entity, session)
        return result
    }

    /**
     * Та же полная запись одного документа без транзакции (1.53.0): фильтр по `_id` и версии атомарен сам по себе, а
     * транзакция на один документ лишь платит коммитом и журналом. Для коллекций без проверок после записи, которым
     * нужна сессия: хуки кеша идут сразу.
     */
    suspend fun replace(entity: T, method: String): UpdateResult {
        // Команда по Idempotency-Key (1.62.0): запись идёт транзакцией, чтобы метка исполнения коммитилась вместе с ней
        val ctx = currentCoroutineContext()
        if (ctx[CommandKey] != null && ctx[TransactionHooks] == null) return MongoFactory.transactionExecute(method) { replace(entity, method, it) }
        return replace(entity, method, null)
    }

    private suspend fun replace(entity: T, method: String, session: ClientSession?): UpdateResult {
        settle(entity)
        val encoded = entityCodec.encode(entity)
        val update = Updates.combine(Versioning.bump(entity) + entityCodec.changes(entity, encoded))
        val result = writing(collectionName, method) { if (session == null) collection.updateOne(Versioning.identity(entity), update) else collection.updateOne(session, Versioning.identity(entity), update) }
        if (result.matchedCount == 0L) missed(method, entity)
        if (entity is VersionedEntity && result.modifiedCount > 0) entity.version += 1
        afterCommit {
            if (entity is TrackedEntity) entity.loaded = encoded
            cache?.updateItem(entity)
        }
        return result
    }

    /**
     * Частичная запись полей [updates] с оптимистичной блокировкой; отвечает документом после записи.
     * Объект [entity] в памяти не меняется. С 1.62.0 - одна запись: поля накладываются на копию,
     * копия приводится к инвариантам ([settle]) и пишется [update] - раньше доролл шёл второй записью.
     */
    suspend fun updateFields(entity: T, updates: Map<String, Any?>, session: ClientSession): T {
        validateBeforeUpdate(updates)
        printLog("[UPDATE::$collectionName] id: ${entity._id}")
        val patched = entityCodec.patched(entity, updates.filterKeys { it != CONST_FIELD_ID && it != CONST_FIELD_VERSION })
        update(patched, session)
        return patched
    }

    /** То же по id: документ читается и пишется с проверкой его текущей версии. */
    suspend fun updateFields(id: String, fields: Map<String, Any?>, session: ClientSession): T? = updateFields(requireById(id) { BaseRepositoryExceptions.funExceptionFindId("updateFields", it) }, fields, session)

    // ==================== DELETE ====================

    /** Жёсткое удаление по id с проверкой версии. */
    suspend fun deleteById(id: String, session: ClientSession): DeleteResult = delete(findById(id), session)

    /** Жёсткое удаление прочитанного документа с проверкой версии. */
    suspend fun deleteById(entity: T, session: ClientSession): DeleteResult = delete(entity, session)

    private suspend fun delete(entity: T?, session: ClientSession): DeleteResult {
        printLog("[DELETE::$collectionName] id: ${entity?._id}")
        if (entity == null) throw BaseRepositoryExceptions.funExceptionEntityNull("delete")
        val result = writing(collectionName, "delete") { collection.deleteOne(session, Versioning.identity(entity)) }
        validateAfterDelete(entity, session)
        if (result.deletedCount == 0L) missed("delete", entity)
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

    /** Запись по [identity] ничего не нашла: документа нет вовсе или его версия ушла вперёд. */
    private suspend fun missed(method: String, entity: T): Nothing {
        val existing = findById(entity._id) ?: throw BaseRepositoryExceptions.funExceptionFindId(method, entity._id)
        throw BaseRepositoryExceptions.funExceptionRace(method, "current: ${Versioning.versionOf(existing)} need: ${Versioning.versionOf(entity)}")
    }

    /** Сущность, которая помнит документ, запоминает его, каким прочла. */
    private fun T.tracked(): T = entityCodec.tracked(this)

    // ==================== ХУКИ ====================

    /** Убирает из документа то, что хранится не в этой коллекции (1.63.0); меняет документ на месте. */
    protected open fun stored(document: BsonDocument) = Unit

    /**
     * Что из присланного разрешено записать: вызывается до [validateBeforeInsert] и может вернуть
     * новый объект. Список разрешённых полей надёжнее обнуления запрещённых - новое поле
     * сущности не станет лазейкой, пока его сюда не добавят.
     */
    protected open suspend fun admit(entity: T): T = entity

    /** Приводит сущность к инвариантам коллекции перед полной записью [update] (0.65.0); меняет её на месте. */
    protected open suspend fun settle(entity: T) = Unit

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

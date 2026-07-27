package base.repository

import base.entity.StockEntity
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
import CONST_FIELD_DELETED
import CONST_FIELD_ID
import CONST_FIELD_UPDATED
import CONST_FIELD_VERSION
import CONST_SYSTEM_FIELDS
import extensions.now
import extensions.printLog
import base.entity.VersionedEntity
import base.exception.BaseRepositoryExceptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.memberProperties

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
abstract class BaseRepository<T : StockEntity>(private val entityClass: KClass<T>) {

    private val collectionName = entityClass.simpleName!!

    // ==================== ПОЛЯ ====================

    /**
     * Коллекция официального драйвера MongoDB Kotlin Coroutines.
     * Используется для низкоуровневых операций с базой данных.
     * 
     * Имя коллекции автоматически берется из имени класса сущности.
     * Например, для класса UserMongo будет создана коллекция "UserMongo".
     */
    var collection: MongoCollection<T> = MongoFactory.getDatabase().getCollection(collectionName, entityClass.java)

    // ==================== ИНИЦИАЛИЗАЦИЯ ====================

    /**
     * Инициализирует репозиторий перед первым использованием:
     * - Создает уникальные индексы (если указаны)
     * - Создает индекс для поля version (для оптимистичной блокировки)
     * - Создает коллекцию в базе данных
     * 
     * Должен быть вызван один раз перед началом работы с репозиторием.
     * Выполняется в контексте runBlocking, поэтому не должен вызываться из корутины.
     * 
     * @param uniqueIndexes Список конфигураций уникальных индексов для создания
     * 
     * Пример:
     * ```
     * repo.initialize(
     *     uniqueIndexes = listOf(
     *         UniqueIndexConfig("unique_email", listOf("email")),
     *         UniqueIndexConfig("unique_username", listOf("username"), sparse = true)
     *     )
     * )
     * ```
     */
    fun initialize(uniqueIndexes: List<UniqueIndexConfig> = emptyList()) {
        runBlocking {
            setupUniqueIndexes(uniqueIndexes)
            setupVersionIndex()
        }
    }

    /**
     * Создаёт уникальные индексы для указанных полей.
     * Обрабатывает случай, когда индекс уже существует (код ошибки 85).
     * 
     * @param indexes Список конфигураций уникальных индексов
     * 
     * Поведение:
     * - При успехе: логирует успешное создание индекса
     * - Если индекс уже существует: логирует информацию и продолжает работу
     * - При других ошибках: выбрасывает BaseRepositoryExceptions
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
     * Создаёт индекс для поля version.
     * 
     * Это поле используется для оптимистичной блокировки при обновлении.
     * Индекс ускоряет проверку совпадения версии при обновлении документа.
     * 
     * Поведение:
     * - При успехе: индекс создается
     * - Если индекс уже существует: игнорирует ошибку (тихое продолжение)
     */
    private suspend fun setupVersionIndex() {
        try {
            collection.createIndex(Indexes.ascending(CONST_FIELD_VERSION))
        } catch (_: Exception) {
            // Индекс уже существует - игнорируем
        }
    }

    // ==================== CREATE ОПЕРАЦИИ ====================

    /**
     * Вставляет один документ в коллекцию.
     * 
     * Проверки:
     * - version должна быть равна 0 (для новых документов)
     * - Вызывает validateBeforeInsert() для пользовательской валидации
     * - Обрабатывает дубликаты (код 11000 - WriteConflict/DuplicateKey)
     * 
     * @param entity Сущность для вставки (должна быть уникальной по уникальным индексам)
     * @param session Сессия транзакции (опционально, может быть null для автотранзакций)
     * @return Вставленная сущность с присвоенным _id
     * 
     * Исключения:
     * - IllegalArgumentException: если version != 0 (пытаемся вставить не новую сущность)
     * - BaseRepositoryExceptions.funExceptionRace: если дубликат по уникальному индексу
     * - BaseRepositoryExceptions.funExceptionInsertInvalid: если MongoDB не подтвердил вставку
     * 
     * Пример:
     * ```
     * val result = repo.insert(UserMongo(email = "test@email.com", name = "Test", age = 25), session)
     * println("ID: ${result._id}")
     * ```
     */
    suspend fun insert(entity: T, session: ClientSession): T {
        if (entity is VersionedEntity && entity.version != 0L) throw BaseRepositoryExceptions.funExceptionInsertVersion("insert", entity.version.toString())
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
     * Вставляет несколько документов в коллекцию атомарно в рамках одной транзакции.
     * 
     * Использует insertMany с сессией транзакции для гарантии атомарности.
     * Все документы вставляются или ни один не вставляется (atomicity).
     * 
     * Проверки:
     * - version каждого документа должна быть равна 0
     * - Вызывает validateBeforeInsert() для каждой сущности
     * - Обрабатывает дубликаты (код 11000)
     * 
     * @param entities Список сущностей для вставки
     * @param session Сессия транзакции (обязательно для atomicity)
     * @return Список сущностей с присвоенными _id
     * 
     * Поведение:
     * - Присваивает сгенерированные ObjectId всем сущностям
     * - Вызывает validateAfterInsert() для каждой вставленной сущности
     * - Логирует количество вставленных документов
     * 
     * Пример:
     * ```
     * val users = listOf(
     *     UserMongo(email = "a@email.com", name = "A", age = 20),
     *     UserMongo(email = "b@email.com", name = "B", age = 25)
     * )
     * val result = repo.insertMany(users, session)
     * println("Вставлено: ${result.size}")
     * ```
     */
    suspend fun insertMany(entities: List<T>, session: ClientSession): List<T> {
        entities.forEach {
            if (it is VersionedEntity && it.version != 0L) throw BaseRepositoryExceptions.funExceptionInsertVersion("insertMany", it.version.toString())
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
     * Поиск документа по ObjectId.
     * 
     * Использует readConcern LOCAL:
     * - Быстрое чтение с любого узла реплика-сета
     * - Может вернуть неподтверждённые данные
     * - Подходит для обычных чтений, где не критична абсолютная свежесть
     * 
     * @param id ID документа
     * @return Найденный документ или null, если не найден
     */
    suspend fun findById(id: ObjectId): T? {
        return collection.find(Filters.eq(CONST_FIELD_ID, id)).firstOrNull()
    }

    /**
     * Поиск документа по ID, преобразуя строковое представление в ObjectId.
     * 
     * Открытый метод для удобства - вызывает findById(ObjectId) внутренне.
     * 
     * @param id Строковое представление ObjectId (24-символьная hex-строка)
     * @return Найденный документ или null
     */
    open suspend fun findById(id: String): T? {
        return findById(ObjectId(id))
    }

    /**
     * Поиск документа по ID с readConcern MAJORITY для операций обновления.
     * 
     * Использует readConcern MAJORITY:
     * - Возвращает данные, подтверждённые большинством узлов реплика-сета
     * - Гарантирует, что данные не будут откачаны (rolled back)
     * - Медленнее, чем LOCAL, но обеспечивает консистентность
     * 
     * Рекомендуется использовать этот метод перед операциями обновления,
     * чтобы избежать ситуации "потерянного обновления" при конкурентном доступе.
     * 
     * @param id ID документа
     * @return Найденный документ или null, если не найден
     */
    suspend fun findByIdForUpdate(id: ObjectId): T? {
        return collection
            .withReadConcern(ReadConcern.MAJORITY)
            .find(Filters.eq(CONST_FIELD_ID, id))
            .firstOrNull()
    }

    /**
     * Возвращает все документы коллекции.
     * 
     * ⚠️ ВНИМАНИЕ: Этот метод может быть очень медленным и потреблять много памяти
     * для больших коллекций. Рекомендуется использовать findByFilterFlow() с фильтрами.
     * 
     * @return Список всех документов (пустой список, если документов нет)
     */
    suspend fun findAll(): List<T> {
        return collection.find().toList()
    }

    /**
     * Поиск документов с пагинацией (pagination).
     * 
     * Реализует классическую пагинацию с offset и limit:
     * - first page:  skip=0, limit=10 → документы 0-9
     * - second page: skip=10, limit=10 → документы 10-19
     * 
     * ⚠️ ВНИМАНИЕ: Для больших значений skip производительность может снижаться,
     * так как MongoDB must scan through skipped documents.
     * Для высоконагруженных систем рекомендуется использовать cursor-based pagination.
     * 
     * @param limit Максимальное количество документов для возврата
     * @param skip Количество документов, которые нужно пропустить (offset)
     * @return Список найденных документов (может быть меньше limit на последней странице)
     * 
     * Пример:
     * ```
     * val firstPage = repo.findLimited(limit = 10, skip = 0)      // первые 10
     * val secondPage = repo.findLimited(limit = 10, skip = 10)     // следующие 10
     * val thirdPage = repo.findLimited(limit = 10, skip = 20)      // следующие 10
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
     * Возвращает Flow, который можно использовать для:
     * - Обработки документов по одному по мере поступления
     * - Применения operator chain (map, filter, collect)
     * - Отслеживания изменений в реальном времени (в комбинации с watch)
     * 
     * @param filter DSL-фильтр MongoDB (используется через Filters DSL)
     * @return Flow найденных документов
     * 
     * Примеры:
     * ```
     * // Найти всех пользователей старше 18 лет
     * repo.findByFilterFlow { UserMongo::age gt 18 }
     *     .collect { user -> println("${user.name} is ${user.age}") }
     * 
     * // Найти пользователей с определенным email
     * repo.findByFilterFlow { UserMongo::email eq "test@email.com" }
     *     .toList()
     * 
     * // Сложный фильтр
     * val filter = and(
     *     gt(UserMongo::age, 18),
     *     lte(UserMongo::age, 35),
     *     eq(UserMongo::status, "active")
     * )
     * repo.findByFilterFlow(filter).toList()
     * ```
     */
    fun findByFilterFlow(filter: Bson): Flow<T> {
        return collection.find(filter)
    }

    /**
     * Поиск документа по значению конкретного поля.
     * 
     * Использует рефлексию для получения имени поля из KMutableProperty1.
     * 
     * @param field Свойство сущности (например, UserMongo::email)
     * @param value Значение для поиска
     * @return Первый найденный документ или null
     * 
     * Пример:
     * ```
     * val user = repo.findByField(UserMongo::email, "test@email.com")
     * if (user != null) {
     *     println("Found: ${user.name}")
     * }
     * ```
     */
    suspend fun <S> findByField(field: KMutableProperty1<T, S>, value: S): T? {
        return collection.find(Filters.eq(field.name, value)).firstOrNull()
    }

    /**
     * Поиск списка документов по значению конкретного поля.
     * 
     * Аналог findByField, но возвращает все совпадения.
     * 
     * @param field Свойство сущности (например, UserMongo::age)
     * @param value Значение для поиска
     * @return Список всех найденных документов (пустой, если ничего не найдено)
     * 
     * Пример:
     * ```
     * val adults = repo.findByFieldList(UserMongo::age, 25)
     * println("Найдено ${adults.size} пользователей возрастом 25")
     * ```
     */
    suspend fun <S> findByFieldList(field: KMutableProperty1<T, S>, value: S): List<T> {
        return collection.find(Filters.eq(field.name, value)).toList()
    }

    /**
     * Поиск документов по произвольному фильтру (синхронная версия).
     * 
     * Аналог findByFilterFlow, но возвращает List вместо Flow.
     * 
     * @param filter DSL-фильтр MongoDB
     * @return Список найденных документов
     */
    suspend fun findByFilter(filter: Bson): List<T> {
        return collection.find(filter).toList()
    }

    /**
     * Отслеживание всех изменений в коллекции (Change Stream).
     * 
     * Возвращает Flow ChangeStreamDocument, который генерирует события:
     * - INSERT: новый документ вставлен
     * - UPDATE: документ обновлен
     * - DELETE: документ удален (только hard delete, не soft)
     * - REPLACE: документ заменен
     * - invalidate: коллекция удалена или переименована
     * 
     * Полезно для:
     * - Реактивных обновлений UI
     * - Синхронизации между сервисами
     * - Event-driven архитектуры
     * 
     * @return Flow событий Change Stream
     * 
     * Пример:
     * ```
     * repo.watchAll()
     *     .collect { changeEvent ->
     *         when (changeEvent.operationType) {
     *             OperationType.INSERT -> println("Новый документ: ${changeEvent.documentKey}")
     *             OperationType.UPDATE -> println("Обновлен: ${changeEvent.documentKey}")
     *             OperationType.DELETE -> println("Удален: ${changeEvent.documentKey}")
     *             else -> Unit
     *         }
     *     }
     * ```
     */
    fun watchAll(): Flow<ChangeStreamDocument<T>> {
        return collection.watch().map { it }
    }

    // ==================== UPDATE ОПЕРАЦИИ ====================

    /**
     * Обновляет сущность с оптимистичной блокировкой (optimistic locking).
     * 
     * Механизм работы:
     * 1. Проверяет, что версия сущности в базе совпадает с версией в объекте
     * 2. Если версии совпадают - обновляет все поля и увеличивает version
     * 3. Если версии не совпадают - выбрасывает исключение (race condition)
     * 
     * Обновляет все поля сущности (кроме _id, version, deleted).
     * Для частичного обновления используйте updateFields().
     * 
     * @param entity Сущность для обновления (должна содержать _id и текущую version)
     * @param session Сессия транзакции
     * @return UpdateResult с информацией о результате операции
     * 
     * Поведение:
     * - matchedCount == 0: документ не найден (удален другим процессом)
     * - matchedCount == 1, modifiedCount == 0: версия не совпала (конфликт обновлений)
     * - matchedCount == 1, modifiedCount == 1: успешное обновление
     * 
     * Исключения:
     * - BaseRepositoryExceptions.funExceptionFindId: если документ не найден
     * - BaseRepositoryExceptions.funExceptionRace: если версия не совпала (конфликт)
     * 
     * Пример:
     * ```
     * val user = repo.findByIdForUpdate(userId) // читаем с MAJORITY для обновления
     * user?.name = "New Name"
     * val result = repo.update(user!!, session)
     * println("Updated version: ${user.version}")
     * ```
     */
    suspend fun update(entity: T, session: ClientSession): UpdateResult {
        val expectedVersion = if (entity is VersionedEntity) entity.version else 0L
        val newVersion = expectedVersion + 1

        val filter = Filters.and(
            Filters.eq(CONST_FIELD_ID, entity._id),
            if (entity is VersionedEntity) Filters.eq(CONST_FIELD_VERSION, expectedVersion) else Filters.empty(),
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
            throw BaseRepositoryExceptions.funException("update", e.message)
        }

        if (result.matchedCount == 0L) {
            val existing = findById(entity._id)
            if (existing == null) {
                throw BaseRepositoryExceptions.funExceptionFindId("update", entity.getId())
            } else {
                throw BaseRepositoryExceptions.funExceptionRace("update", "current: ${if (existing is VersionedEntity) existing.version else 0L} need: $expectedVersion")
            }
        }

        if (entity is VersionedEntity) {
            if (result.wasAcknowledged() && result.modifiedCount > 0) {
                entity.version = newVersion
            }
        }

        return result
    }

    /**
     * Частичное обновление документа с оптимистичной блокировкой.
     * 
     * Обновляет только указанные поля, игнорируя остальные.
     * Использует findOneAndUpdate с опцией returnDocument = AFTER,
     * что возвращает обновлённый документ в одном запросе.
     * 
     * ⚠️ ВНИМАНИЕ: Этот метод НЕ обновляет версию сущности в памяти.
     * Если нужна актуальная версия, вызовите findById после updateFields.
     * 
     * @param entity Сущность с ID и текущей version для проверки
     * @param updates Map полей и новых значений ( field -> value )
     * @param session Сессия транзакции
     * @return Обновлённый документ (после применения изменений)
     * 
     * Пример:
     * ```
     * val updatedUser = repo.updateFields(
     *     entity = user,
     *     updates = mapOf(
     *         "name" to "New Name",
     *         "age" to 30,
     *         "status" to "active"
     *     ),
     *     session = session
     * )
     * println("Updated name: ${updatedUser.name}")
     * ```
     * 
     * @throws BaseRepositoryExceptions.funExceptionFindId: если после обновления документ не найден
     */
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
            if (entity is VersionedEntity) Filters.eq(CONST_FIELD_VERSION, expectedVersion) else Filters.empty()
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
            throw BaseRepositoryExceptions.funException("updateFields", e.message)
        }

        if (result == null) {
            throw BaseRepositoryExceptions.funException("updateFields", "Not found object with id ${entity.getId()} after update")
        }

        return result
    }

    /**
     * Частичное обновление документа по ID (удобный метод).
     * 
     * Сначала находит сущность по ID, затем вызывает updateFields(entity, updates, session).
     * 
     * @param id ID документа для обновления
     * @param fields Map полей и новых значений
     * @param session Сессия транзакции
     * @return Обновлённый документ или null, если не найден
     */
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
     * Удаляет документ по ID с проверкой версии (hard delete).
     * 
     * Сначала находит сущность по ID, затем вызывает deleteWithVersion.
     * Использует оптимистичную блокировку для предотвращения удаления
     * изменённого другим процессом документа.
     * 
     * @param id ID документа для удаления
     * @param session Сессия транзакции
     * @return DeleteResult с информацией о результате операции
     */
    suspend fun deleteById(id: ObjectId, session: ClientSession): DeleteResult {
        val findedObj = findById(id)
        return deleteWithVersion(findedObj, session)
    }

    /**
     * Удаляет документ по строковому ID (удобный метод).
     * 
     * Преобразует строку в ObjectId и вызывает deleteById(ObjectId, session).
     * 
     * @param id Строковое представление ObjectId
     * @param session Сессия транзакции
     * @return DeleteResult
     */
    suspend fun deleteById(id: String, session: ClientSession): DeleteResult {
        return deleteById(ObjectId(id), session)
    }

    /**
     * Удаляет сущность с проверкой версии (hard delete).
     * 
     * Вызывает deleteWithVersion с переданной сущностью.
     * 
     * @param entity Сущность для удаления (должна содержать _id и version)
     * @param session Сессия транзакции
     * @return DeleteResult
     */
    suspend fun deleteById(entity: T, session: ClientSession): DeleteResult {
        return deleteWithVersion(entity, session)
    }

    /**
     * Удаляет сущность с проверкой версии (hard delete).
     * 
     * Использует оптимистичную блокировку:
     * - Фильтр по _id и version
     * - Если версия не совпала - документ был изменён другим процессом
     * - Бросает исключение BaseRepositoryExceptions.funExceptionRace
     * 
     * После успешного удаления вызывает validateAfterDelete.
     * 
     * @param entity Сущность для удаления (должна содержать _id и version)
     * @param session Сессия транзакции
     * @return DeleteResult с information о результате
     * 
     * Исключения:
     * - BaseRepositoryExceptions.funExceptionEntityNull: если entity == null
     * - BaseRepositoryExceptions.funExceptionRace: если версия не совпала
     * 
     * Пример:
     * ```
     * val user = repo.findByIdForUpdate(userId) // читаем с MAJORITY
     * val result = repo.deleteWithVersion(user, session)
     * println("Deleted: ${result.deletedCount} documents")
     * ```
     */
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

    /**
     * Мягкое удаление документа (soft delete).
     * 
     * Устанавливает поле deleted = true вместо физического удаления.
     * Документ остаётся в базе, но считается "удалённым".
     * 
     * ⚠️ Требует, чтобы сущность наследовалась от VersionedEntity.
     * 
     * @param id ID документа для мягкого удаления
     * @param session Сессия транзакции
     * @return UpdateResult с информацией о результате
     * 
     * Исключения:
     * - BaseRepositoryExceptions.funExceptionFindId: если документ не найден
     * - BaseRepositoryExceptions.funExceptionVersioned: если сущность не VersionedEntity
     * 
     * Пример:
     * ```
     * // Поиск "не удалённых" документов
     * val filter = Filters.eq("deleted", false)
     * val activeUsers = repo.findByFilter(filter)
     * 
     * // Мягкое удаление
     * repo.softDelete(userId, session)
     * ```
     */
    suspend fun softDelete(id: ObjectId, session: ClientSession): UpdateResult {
        printLog("[SOFT_DELETE::$collectionName] id: $id")

        val findedObj = findById(id)
        if (findedObj == null) {
            throw BaseRepositoryExceptions.funExceptionFindId("softDelete", id.toHexString())
        }

        if (findedObj !is VersionedEntity) {
            throw BaseRepositoryExceptions.funExceptionVersioned("softDelete", findedObj::class.simpleName)
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
     * Мягкое удаление документа по сущности (удобный метод).
     * 
     * @param entity Сущность для мягкого удаления
     * @param session Сессия транзакции
     * @return UpdateResult
     */
    suspend fun softDelete(entity: T, session: ClientSession): UpdateResult {
        return softDelete(entity._id, session)
    }

    /**
     * Восстановление мягкого удалённого документа (restore).
     * 
     * Устанавливает поле deleted = false.
     * Документ становится доступен для обычных запросов снова.
     * 
     * @param id ID документа для восстановления
     * @param session Сессия транзакции
     * @return UpdateResult с информацией о результате
     * 
     * Пример:
     * ```
     * // Восстановление документа
     * repo.restore(userId, session)
     * 
     * // Поиск всех (включая удалённые)
     * val allUsers = repo.collection.find().toList()
     * 
     * // Поиск только активных (не удалённых)
     * val activeFilter = Filters.eq("deleted", false)
     * val activeUsers = repo.collection.find(activeFilter).toList()
     * ```
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
     * Восстановление мягкого удалённого документа по сущности (удобный метод).
     * 
     * @param entity Сущность для восстановления
     * @param session Сессия транзакции
     * @return UpdateResult
     */
    suspend fun restore(entity: T, session: ClientSession): UpdateResult {
        return restore(entity._id, session)
    }

    // ==================== BULK ОПЕРАЦИИ ====================

    /**
     * Массовое обновление нескольких документов с оптимистичной блокировкой.
     * 
     * Выполняет bulkWrite с UpdateOneModel для каждой сущности.
     * Каждая операция проверяет версию перед обновлением.
     * 
     * Поведение:
     * - Если версия не совпала для какой-то сущности - она пропускается
     * - matchedCount показывает, сколько операций нашли совпадение
     * - modifiedCount показывает, сколько операций реально изменили документ
     * 
     * @param entities Список сущностей для обновления (должны содержать _id и version)
     * @param session Сессия транзакции
     * @return BulkWriteResult с информацией о результате массовой операции
     * 
     * Пример:
     * ```
     * val usersToUpdate = listOf(user1, user2, user3)
     * val result = repo.bulkUpdate(usersToUpdate, session)
     * 
     * println("Matched: ${result.matchedCount}")
     * println("Modified: ${result.modifiedCount}")
     * ```
     */
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
            throw BaseRepositoryExceptions.funException("bulkUpdate", e.message)
        }

        return result
    }

    // ==================== HELPER МЕТОДЫ ====================

    /**
     * Проверка существования документа по ID.
     * 
     * @param id ID документа
     * @param withDeleted Если true, ищет даже среди удалённых документов (deleted = true)
     * @return true, если документ существует, false иначе
     */
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

    /**
     * Подсчёт количества документов в коллекции.
     * 
     * @param filter DSL-фильтр (опционально)
     * @return Количество документов,匹配ющих фильтру
     * 
     * Пример:
     * ```
     * val total = repo.count() // все документы
     * val adults = repo.count { UserMongo::age gt 18 } // только взрослые
     * ```
     */
    suspend fun count(filter: Bson = Filters.empty()): Long {
        return collection.countDocuments(filter)
    }

    /**
     * Поиск с пагинацией и возвратом результатов в формате PagedMongoResponse.
     * 
     * Вычисляет:
     * - items: текущая страница данных
     * - page: номер текущей страницы
     * - pageSize: размер страницы
     * - total: общее количество документов
     * - pages: общее количество страниц
     * 
     * @param page Номер страницы (начиная с 0)
     * @param pageSize Размер страницы (количество документов на странице)
     * @return PagedMongoResponse с информацией о пагинации
     * 
     * Пример:
     * ```
     * val response = repo.findPaged(page = 0, pageSize = 20)
     * println("Всего документов: ${response.total}")
     * println("Всего страниц: ${response.pages}")
     * println("На странице: ${response.items.size}")
     * 
     * // Переход на следующую страницу
     * val nextPage = repo.findPaged(page = 1, pageSize = 20)
     * ```
     */
    suspend fun findPaged(page: Int, pageSize: Int = 20): PagedMongoResponse<T> {
        val items = findLimited(page, pageSize)
        val total = count()
        val pages = if (pageSize > 0) ((total + pageSize - 1) / pageSize).toInt() else 0
        return PagedMongoResponse(items, page, pageSize, total, pages)
    }

    // ==================== ПРИВАТНЫЕ МЕТОДЫ ====================

    /**
     * Получение всех полей сущности для обновления.
     * 
     * Использует рефлексию (kotlin.reflect) для получения всех memberProperties.
     * Исключает служебные поля из CONST_SYSTEM_FIELDS:
     * - _id (CONST_FIELD_ID)
     * - version (CONST_FIELD_VERSION)
     * - deleted (CONST_FIELD_DELETED)
     * - updated (CONST_FIELD_UPDATED)
     * 
     * @param entity Сущность для анализа
     * @return Map полей и их значений для использования в Updates
     */
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

    /**
     * Удаляет всю коллекцию (drop collection).
     * 
     * ⚠️ ОПАСНАЯ ОПЕРАЦИЯ:
     * - Удаляет всю коллекцию целиком
     * - Удаляет все индексы
     * - Возвращает MongoDB в состояние "как после создания"
     * 
     * Используется для тестирования или полной очистки данных.
     * Вызывает drop() из драйвера MongoDB.
     */
    suspend fun deleteAll() {
        printLog("[DELETE_All::$collectionName]")
        collection.drop()
    }

    // ==================== АБСТРАКТНЫЕ МЕТОДЫ ====================

    /**
     * Проверка перед вставкой новой сущности.
     * 
     * Переопределяется в классах для реализации пользовательской валидации.
     * Вызывается автоматически перед insert и insertMany.
     * 
     * @param entity Сущность для валидации
     * 
     * Пример:
     * ```
     * class UserRepository : BaseRepository<UserMongo>(UserMongo::class) {
     *     override suspend fun validateBeforeInsert(entity: UserMongo) {
     *         if (entity.email.isNullOrBlank()) {
     *             throw IllegalArgumentException("Email cannot be blank")
     *         }
     *         if (entity.age < 0) {
     *             throw IllegalArgumentException("Age cannot be negative")
     *         }
     *     }
     * }
     * ```
     */
    protected open suspend fun validateBeforeInsert(entity: T) {
        // Базовая реализация — ничего не проверяем
    }

    /**
     * Валидация изменений перед частичным обновлением (updateFields).
     * 
     * Переопределяется в классах для проверки доступности полей для обновления.
     * Вызывается автоматически перед updateFields.
     * 
     * @param changes Map полей и новых значений для валидации
     * 
     * Пример:
     * ```
     * override suspend fun validateBeforeUpdate(changes: Map<String, Any?>) {
     *     if (changes.containsKey("isAdmin") && !currentUser.isSuperuser) {
     *         throw SecurityException("Only superuser can change isAdmin")
     *     }
     * }
     * ```
     */
    protected open suspend fun validateBeforeUpdate(changes: Map<String, Any?>) {
        // Базовая реализация — ничего не проверяем
    }

    /**
     * Действия после успешной вставки сущности.
     * 
     * Переопределяется в классах для выполнения логики:
     * - Кеширование
     * - Отправка событий
     * - Логирование
     * - Синхронизация с другими сервисами
     * 
     * Вызывается после insert и insertMany, когда сущность уже вставлена.
     * 
     * @param entity Вставленная сущность с присвоенным _id
     * @param session Сессия транзакции (в контексте которой была вставка)
     */
    protected open suspend fun validateAfterInsert(entity: T, session: ClientSession) {

    }

    /**
     * Действия после удаления сущности (hard delete или soft delete).
     * 
     * Переопределяется в классах для выполнения логики:
     * - Удаление из кеша
     * - Отправка событий об удалении
     * - Логирование операции
     * - Синхронизация с другими сервисами
     * 
     * Вызывается после deleteWithVersion и softDelete.
     * 
     * @param entity Удалённая сущность
     * @param session Сессия транзакции
     * @param softDelete true, если это было мягкое удаление, false - hard delete
     */
    protected open suspend fun validateAfterDelete(entity: T, session: ClientSession, softDelete: Boolean) {

    }
}
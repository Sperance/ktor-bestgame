package base.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.datetime
import java.time.LocalDateTime

/**
 * Абстрактная базовая таблица, предоставляющая стандартные колонки для всех сущностей.
 *
 * **Основная идея:** Унификация структуры таблиц и гарантия наличия обязательных
 * служебных полей для корректной работы ORM и оптимистичной блокировки.
 *
 * **Стандартные колонки:**
 * - `id` — первичный ключ, автоинкрементируемый идентификатор
 * - `version` — версия записи для оптимистичной блокировки
 * - `created_at` — временная метка создания записи
 * - `updated_at` — временная метка последнего обновления
 *
 * **Зачем это нужно:**
 * 1. **Оптимистичная блокировка** через поле `version` предотвращает
 *    конкурентные конфликты при одновременном обновлении
 * 2. **Аудит** — поля `created_at`/`updated_at` позволяют отслеживать
 *    историю изменений записей
 * 3. **Унификация** — все таблицы имеют одинаковую структуру служебных полей,
 *    что упрощает обобщённый код (репозитории, сервисы, роуты)
 *
 * **Требования к наследникам:**
 * - Должны вызывать конструктор с именем таблицы
 * - Могут добавлять свои колонки
 * - Могут переопределять первичный ключ (но обычно это не требуется)
 *
 * @param name Имя таблицы в базе данных (например, "users", "products", "orders")
 *
 * @see base.model.BaseEntity
 * @see base.repository.BaseRepository
 * @see base.service.BaseService
 * @see base.exception.OptimisticLockException
 *
 * @sample
 * ```kotlin
 * // Пример конкретной таблицы
 * object UsersTable : BaseTable("users") {
 *     val name = varchar("name", 100)
 *     val email = varchar("email", 100).uniqueIndex()
 *     val age = integer("age").nullable()
 *     val isActive = bool("is_active").default(true)
 * }
 *
 * // Таблица с внешним ключом
 * object OrdersTable : BaseTable("orders") {
 *     val userId = reference("user_id", UsersTable.id)
 *     val total = decimal("total", 10, 2)
 *     val status = enumeration("status", OrderStatus::class)
 * }
 *
 * // Использование в запросах
 * fun insertUser(name: String, email: String) = transaction {
 *     UsersTable.insert { stmt ->
 *         stmt[UsersTable.name] = name
 *         stmt[UsersTable.email] = email
 *         // version, created_at, updated_at заполнятся автоматически
 *     }
 * }
 * ```
 */
abstract class BaseTable(name: String) : Table(name) {

    /**
     * Первичный ключ — уникальный идентификатор записи.
     *
     * **Характеристики:**
     * - Тип: `Long` (64-битное целое число)
     * - Автоинкремент — значение генерируется базой данных
     * - Обязателен для всех записей
     * - Является первичным ключом таблицы
     *
     * **Использование:**
     * - Для поиска записи по ID
     * - Для связей (внешних ключей) между таблицами
     * - В пагинации (сортировка по ID)
     *
     * **Важно:** Значение обычно не устанавливается вручную,
     * а генерируется БД при вставке.
     *
     * @sample
     * ```kotlin
     * // Поиск по ID
     * val user = UsersTable.selectAll()
     *     .where { UsersTable.id eq userId }
     *     .singleOrNull()
     *
     * // Сортировка по ID
     * val users = UsersTable.selectAll()
     *     .orderBy(UsersTable.id to SortOrder.ASC)
     * ```
     */
    val id = long("id").autoIncrement()

    /**
     * Версия записи для оптимистичной блокировки.
     *
     * **Механизм работы:**
     * 1. При создании записи version = 1
     * 2. При каждом обновлении version увеличивается на 1
     * 3. При обновлении проверяется: `WHERE id = :id AND version = :oldVersion`
     * 4. Если версия не совпала → обновление не происходит
     *
     * **Зачем это нужно:**
     * - Предотвращает потерю обновлений при конкурентном доступе
     * - Позволяет клиенту узнать, что данные изменились
     * - Не требует блокировок таблиц/строк
     *
     * **Пример конфликта:**
     * ```
     * Пользователь A: Загрузил версию 1
     * Пользователь B: Загрузил версию 1
     * Пользователь A: Обновил → version = 2
     * Пользователь B: Обновляет с version=1 → ОШИБКА (OptimisticLockException)
     * ```
     *
     * **Значение по умолчанию:** 1L
     *
     * @see base.exception.OptimisticLockException
     *
     * @sample
     * ```kotlin
     * // Обновление с проверкой версии
     * val updatedRows = UsersTable.update(
     *     where = { (UsersTable.id eq userId) and (UsersTable.version eq oldVersion) }
     * ) {
     *     it[UsersTable.name] = newName
     *     it[UsersTable.version] = oldVersion + 1
     * }
     *
     * if (updatedRows == 0) {
     *     throw OptimisticLockException("User was modified by another user")
     * }
     * ```
     */
    val version = long("version").default(1L)

    /**
     * Временная метка создания записи.
     *
     * **Характеристики:**
     * - Тип: `LocalDateTime` (дата и время без часового пояса)
     * - Устанавливается один раз при вставке записи
     * - Не изменяется при обновлениях
     * - Использует `clientDefault`, что позволяет клиенту (Kotlin коду)
     *   установить значение, если оно не предоставлено
     *
     * **Установка значения:**
     * - Если значение не указано → используется `LocalDateTime.now()`
     * - Можно явно установить своё значение
     * - База данных может переопределить (например, DEFAULT CURRENT_TIMESTAMP)
     *
     * **Использование:**
     * - Аудит — когда запись была создана
     * - Сортировка — новые/старые записи
     * - Отчёты — фильтрация по дате создания
     * - Очистка данных — удаление старых записей
     *
     * @sample
     * ```kotlin
     * // Поиск записей за последние 7 дней
     * val weekAgo = LocalDateTime.now().minusDays(7)
     * val recent = UsersTable.selectAll()
     *     .where { UsersTable.createdAt greaterEq weekAgo }
     *
     * // Сортировка от новых к старым
     * val newestFirst = UsersTable.selectAll()
     *     .orderBy(UsersTable.createdAt to SortOrder.DESC)
     * ```
     */
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }

    /**
     * Временная метка последнего обновления записи.
     *
     * **Характеристики:**
     * - Тип: `LocalDateTime` (дата и время без часового пояса)
     * - Обновляется при каждой модификации записи
     * - Использует `clientDefault`, но обычно переопределяется при UPDATE
     *
     * **Автоматическое обновление:**
     * - При создании: устанавливается текущее время
     * - При обновлении: ДОЛЖНО обновляться явно в коде
     * - Некоторые БД поддерживают `ON UPDATE CURRENT_TIMESTAMP`
     *
     * **Использование:**
     * - Аудит — когда запись была изменена в последний раз
     * - Кэширование — инвалидация кэша по дате обновления
     * - Синхронизация — обнаружение изменений для репликации
     * - Оптимизация — пропуск неизменённых данных
     *
     * **Важно:** В отличие от `createdAt`, это поле НЕ обновляется автоматически
     * при UPDATE в Exposed. Нужно явно устанавливать в коде:
     * ```kotlin
     * stmt[table.updatedAt] = LocalDateTime.now()
     * ```
     *
     * @sample
     * ```kotlin
     * // Автоматическое обновление при UPDATE
     * table.update({ table.id eq id }) { stmt ->
     *     stmt[table.name] = newName
     *     stmt[table.updatedAt] = LocalDateTime.now() // ← обязательно!
     * }
     *
     * // Поиск недавно изменённых записей
     * val recentlyUpdated = UsersTable.selectAll()
     *     .where { UsersTable.updatedAt greaterEq LocalDateTime.now().minusHours(1) }
     * ```
     */
    val updatedAt = datetime("updated_at").clientDefault { LocalDateTime.now() }

    /**
     * Определяет первичный ключ таблицы.
     *
     * По умолчанию — колонка `id`.
     * В редких случаях может быть переопределён в наследниках
     * (например, для таблиц с составным ключом).
     *
     * @sample
     * ```kotlin
     * // Пример составного первичного ключа
     * object UserRolesTable : BaseTable("user_roles") {
     *     val userId = reference("user_id", UsersTable.id)
     *     val roleId = reference("role_id", RolesTable.id)
     *
     *     override val primaryKey = PrimaryKey(userId, roleId)
     * }
     * ```
     */
    override val primaryKey = PrimaryKey(id)
}
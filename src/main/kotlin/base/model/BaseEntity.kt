package base.model

/**
 * Базовый контракт для всех сущностей (Entity) приложения.
 *
 * Определяет минимальный набор полей, который обязана иметь каждая сущность
 * для корректной работы инфраструктуры: идентификация ([id]) и
 * контроль конкурентного доступа ([version]).
 *
 * ## Философия единой модели
 *
 * В классическом подходе для каждой сущности создаётся три отдельных класса:
 *
 * ```kotlin
 * // ❌ Классический подход — дублирование полей:
 * data class UserResponse(val id: Long, val name: String, val email: String, ...)
 * data class CreateUserRequest(val name: String, val email: String, ...)
 * data class UpdateUserRequest(val name: String?, val email: String?, ...)
 * ```
 *
 * Вместо этого используется **одна модель** с аннотациями, которые
 * определяют поведение каждого поля при создании и обновлении:
 *
 * ```kotlin
 * // ✅ Единая модель — поля описаны один раз:
 * @Serializable
 * data class User(
 *     @ReadOnly  override val id: Long? = null,     // не принимается от клиента
 *                val name: String,                   // обязательно при создании, изменяемо
 *                val email: String,                  // обязательно при создании, изменяемо
 *     @Immutable val authorId: Long,                 // обязательно при создании, НЕ изменяемо
 *     @ReadOnly  override val version: Long = 1,     // управляется сервером
 *     @ReadOnly  val createdAt: String? = null,      // управляется сервером
 * ) : BaseEntity
 * ```
 *
 * Система через рефлексию и аннотации сама определяет:
 * - **CREATE**: принимает все поля без `@ReadOnly`
 * - **UPDATE**: принимает все поля без `@ReadOnly` и без `@Immutable`
 * - **RESPONSE**: отдаёт все поля, включая `@ReadOnly`
 *
 * ## Optimistic Locking через [version]
 *
 * Поле [version] — ключевой элемент защиты от race conditions
 * при конкурентном обновлении одной и той же записи:
 *
 * ```
 * Клиент A: GET /users/1 → { name: "Alice", version: 3 }
 * Клиент B: GET /users/1 → { name: "Alice", version: 3 }
 *
 * Клиент A: PUT /users/1 { name: "Bob",   version: 3 } → 200 OK, version: 4
 * Клиент B: PUT /users/1 { name: "Carol", version: 3 } → 409 Conflict!
 *                                                          (version 3 уже не актуальна)
 * ```
 *
 * На уровне SQL это реализуется через условие в WHERE:
 *
 * ```sql
 * UPDATE users
 * SET name = 'Bob', version = 4, updated_at = now()
 * WHERE id = 1 AND version = 3;
 * -- Если affected rows = 0 → OptimisticLockException
 * ```
 *
 * ## Взаимодействие с другими компонентами
 *
 * ```
 * BaseEntity
 *    │
 *    ├── BaseTable          — таблица содержит колонки id и version
 *    │
 *    ├── BaseRepository     — INSERT ставит version = 1,
 *    │                        UPDATE проверяет version в WHERE
 *    │                        и инкрементирует при успехе
 *    │
 *    ├── ReflectiveMapper   — при toEntity() маппит колонки id, version
 *    │                        из ResultRow в соответствующие поля Entity
 *    │
 *    ├── BaseService        — передаёт version из JSON клиента
 *    │                        в repository.update()
 *    │
 *    └── BaseRoute          — использует KSerializer<E> (где E : BaseEntity)
 *                             для корректной сериализации ответов
 * ```
 *
 * ## Требования к реализации
 *
 * Каждая реализация должна:
 * 1. Быть `@Serializable` data class (для JSON-сериализации и сравнений)
 * 2. Пометить [id] и [version] аннотацией `@ReadOnly` (управляются сервером)
 * 3. Задать значения по умолчанию: `id = null` (до сохранения в БД), `version = 1`
 *
 * ```kotlin
 * @Serializable
 * data class User(
 *     @ReadOnly override val id: Long? = null,
 *     val name: String,
 *     @ReadOnly override val version: Long = 1
 * ) : BaseEntity
 * ```
 *
 * @property id      Уникальный идентификатор записи в базе данных.
 *                   `null` до первого сохранения (INSERT), после чего
 *                   присваивается автоинкрементное значение из PostgreSQL.
 *                   Помечается `@ReadOnly` — клиент не может задать или изменить.
 *
 * @property version Номер версии записи для optimistic locking.
 *                   Начинается с `1` при создании. Инкрементируется на `1`
 *                   при каждом успешном UPDATE. Клиент обязан передавать
 *                   актуальную version при обновлении — если она не совпадает
 *                   с текущей в БД, сервер отвечает `409 Conflict`.
 *                   Помечается `@ReadOnly` — значение управляется исключительно сервером.
 *
 * @see base.annotations.ReadOnly     Поле только для чтения (id, version, timestamps)
 * @see base.annotations.Immutable    Поле неизменяемо после создания (authorId)
 * @see base.annotations.DefaultValue Значение по умолчанию при создании
 * @see base.table.BaseTable           Базовая таблица с колонками id, version
 * @see base.repository.BaseRepository Реализация optimistic locking в SQL
 * @see base.exception.OptimisticLockException Исключение при конфликте версий
 */
interface BaseEntity {
    val id: Long?
    val version: Long
}
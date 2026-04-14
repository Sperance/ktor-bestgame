package base.annotations

/**
 * Свойство не маппится в колонку таблицы.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class Unmapped

/**
 * Явное имя колонки (если отличается от camelCase → snake_case).
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class ColumnName(val value: String)

/**
 * Поле нельзя менять после создания.
 * При update — игнорируется.
 * Пример: authorId в Post.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class Immutable

/**
 * Поле только для чтения — не принимается ни при create, ни при update.
 * Управляется системой (id, version, createdAt, updatedAt).
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class ReadOnly

/**
 * Поле обязательно при создании (не может быть null).
 * По умолчанию все non-null поля без default считаются required,
 * но эта аннотация позволяет явно пометить.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class Required

/**
 * Значение по умолчанию при создании, если поле не передано.
 * Хранится как строка, парсится в нужный тип.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class DefaultValue(val value: String)

/**
 * Поле только для записи — принимается при create/update,
 * но при чтении из БД (toEntity) подставляется Kotlin-дефолт конструктора
 * вместо реального значения из ResultRow.
 *
 * Используется для секретных полей (password, salt, token),
 * которые не должны попадать в JSON-ответ клиенту.
 *
 * Пример:
 * ```kotlin
 * @WriteOnly
 * val password: String = "",
 *
 * @WriteOnly
 * @ReadOnly           // ← можно комбинировать: ReadOnly + WriteOnly = системное секретное поле
 * val salt: String = "",
 * ```
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class WriteOnly
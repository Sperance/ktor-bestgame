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
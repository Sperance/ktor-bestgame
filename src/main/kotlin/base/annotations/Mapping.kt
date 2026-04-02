package base.annotations

/**
 * Свойство не маппится в колонку таблицы.
 * Используется для вычисляемых / транзиентных полей в DTO.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class Unmapped

/**
 * Явное указание имени колонки, если оно не выводится
 * из camelCase → snake_case автоматически.
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class ColumnName(val value: String)
package base.reflection

import org.jetbrains.exposed.v1.core.Column
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1

/**
 * Закэшированная привязка одного Kotlin-свойства к колонке таблицы.
 */
data class PropertyDescriptor(
    /** Свойство класса (например, `User::name`) */
    val property: KProperty1<out Any, *>,
    /** Колонка таблицы (например, `UsersTable.name`) */
    val column: Column<*>,
    /** Соответствующий параметр primary-конструктора (для создания экземпляра) */
    val constructorParam: KParameter?
)
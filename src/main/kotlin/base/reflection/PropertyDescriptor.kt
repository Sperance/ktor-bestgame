package base.reflection

import org.jetbrains.exposed.v1.core.Column
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1

/**
 * Закэшированная привязка одного Kotlin-свойства к колонке таблицы.
 *
 * Этот класс служит мостом между объектной моделью (Kotlin-классы) и реляционной моделью (таблицы БД).
 * Он хранит информацию о том, как конкретное свойство класса сопоставляется с определённой колонкой
 * таблицы, а также содержит метаинформацию о параметре конструктора, необходимую для
 * обратного преобразования строки результата запроса в экземпляр класса.
 *
 * Используется внутри ORM для кэширования результатов рефлексии и ускорения маппинга
 * между объектами и записями в базе данных.
 *
 * @property property Свойство класса, представляющее поле в объектной модели.
 *                    Например: `User::name`, `Product::price`.
 *                    Тип `KProperty1<out Any, *>` означает свойство с одним получателем
 *                    (экземпляром класса), возвращающее значение произвольного типа.
 *
 * @property column Колонка таблицы базы данных, соответствующая данному свойству.
 *                  Например: `UsersTable.name`, `ProductsTable.price`.
 *                  Тип `Column<*>` представляет колонку в DSL фреймворка (например, Exposed).
 *
 * @property constructorParam Соответствующий параметр primary-конструктора класса.
 *                            Используется при создании экземпляра класса из результатов запроса.
 *                            Позволяет понять, какое значение из какой колонки передавать
 *                            в какой параметр конструктора.
 *                            Может быть `null`, если свойство не является параметром конструктора
 *                            (например, вычисляемое поле или свойство с делегатом).
 *                            Тип: `KParameter?` — параметр конструктора из Kotlin reflection.
 *
 * @sample
 * ```kotlin
 * // Пример класса-сущности
 * class User(val id: Int, val name: String, val email: String)
 *
 * // Пример таблицы (Exposed DSL)
 * object UsersTable : Table() {
 *     val id = integer("id").autoIncrement()
 *     val name = varchar("name", 50)
 *     val email = varchar("email", 100)
 * }
 *
 * // Создание дескрипторов свойств
 * val descriptors = listOf(
 *     PropertyDescriptor(
 *         property = User::id,
 *         column = UsersTable.id,
 *         constructorParam = User::class.primaryConstructor?.parameters?.find { it.name == "id" }
 *     ),
 *     PropertyDescriptor(
 *         property = User::name,
 *         column = UsersTable.name,
 *         constructorParam = User::class.primaryConstructor?.parameters?.find { it.name == "name" }
 *     )
 * )
 * ```
 *
 * @see KProperty1
 * @see Column
 * @see KParameter
 */
data class PropertyDescriptor(
    val property: KProperty1<out Any, *>,
    val column: Column<*>,
    val constructorParam: KParameter?
)
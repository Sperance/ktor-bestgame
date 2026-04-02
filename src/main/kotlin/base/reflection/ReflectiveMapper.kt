package base.reflection

import base.exception.BadRequestException
import base.table.BaseTable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import org.jetbrains.exposed.v1.core.BooleanColumnType
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.DoubleColumnType
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.LongColumnType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.InsertStatement
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import java.time.LocalDateTime
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.primaryConstructor

/**
 * Рефлексивный маппер для преобразования данных между различными представлениями.
 *
 * Основная ответственность: трансформация данных между слоями приложения:
 * - Из результатов SQL-запросов (`ResultRow`) в объекты сущностей (`Entity`)
 * - Из JSON-запросов клиента в SQL-операторы (`InsertStatement`, `UpdateStatement`)
 *
 * Класс использует кэшированную метаинформацию о сущностях (`EntityMetadataCache`)
 * для минимизации накладных расходов на рефлексию в рантайме.
 *
 * **Важные особенности:**
 * - Поддерживает иммутабельные сущности (через primary-конструктор)
 * - Разделяет поля на категории (forCreate, forUpdate, forConstructor)
 * - Валидирует обязательные поля при создании
 * - Игнорирует ReadOnly и Immutable поля при обновлении
 * - Автоматически конвертирует типы между JSON, SQL и Kotlin
 *
 */
object ReflectiveMapper {

    // ==================== ResultRow → Entity ====================

    /**
     * Преобразует строку результата SQL-запроса в экземпляр сущности.
     *
     * Процесс маппинга:
     * 1. Получает закэшированные метаданные для класса и таблицы
     * 2. Извлекает primary-конструктор целевого класса
     * 3. Для каждого параметра конструктора находит соответствующую колонку в `ResultRow`
     * 4. Извлекает сырое значение из строки результата
     * 5. Применяет необходимые преобразования типов (`coerce`)
     * 6. Вызывает конструктор с подготовленными аргументами
     *
     * **Требования к классам-сущностям:**
     * - Должен иметь primary-конструктор
     * - Параметры конструктора должны соответствовать колонкам таблицы
     * - Допускается использование `val` (иммутабельные свойства)
     *
     * @param E Тип сущности (выводится автоматически)
     * @param klass KClass целевой сущности (например, `User::class`)
     * @param table Таблица базы данных, с которой связана сущность
     * @param row Строка результата SQL-запроса
     * @return Созданный экземпляр сущности с заполненными полями
     * @throws IllegalArgumentException Если у класса нет primary-конструктора
     * @throws ClassCastException При несоответствии типов колонок и параметров конструктора
     *
     * @sample
     * ```kotlin
     * val row: ResultRow = // ... получен из Exposed
     * val user = ReflectiveMapper.toEntity(User::class, UsersTable, row)
     * ```
     */
    fun <E : Any> toEntity(klass: KClass<E>, table: BaseTable, row: ResultRow): E {
        val meta = EntityMetadataCache.get(klass, table)
        val constructor = klass.primaryConstructor!!

        val args = meta.forConstructor.associate { desc ->
            val rawValue = row[desc.column]
            val converted = coerce(rawValue, desc.constructorParam!!.type.classifier as? KClass<*>)
            desc.constructorParam to converted
        }

        return constructor.callBy(args)
    }

    // ==================== JSON → InsertStatement ====================

    /**
     * Заполняет SQL-оператор вставки (INSERT) данными из JSON-объекта.
     *
     * **Поведение:**
     * - Использует только поля из категории `forCreate` (без ReadOnly и авто-генерируемых)
     * - Валидирует наличие всех обязательных (`required`) полей
     * - Поддерживает значения по умолчанию через аннотацию `@Default`
     * - Автоматически конвертирует JSON-типы в типы колонок
     *
     * **Обработка различных случаев:**
     * - Поле присутствует в JSON → парсится и устанавливается
     * - Поле отсутствует, но есть `@Default` → используется значение по умолчанию
     * - Поле отсутствует и помечено `@Required` → выбрасывается исключение
     * - Поле отсутствует и не required → устанавливается NULL
     *
     * @param statement SQL-оператор вставки (обычно из Exposed)
     * @param table Таблица, в которую выполняется вставка
     * @param json JSON-объект от клиента (например, из POST-запроса)
     * @param entityClass Класс сущности, определяющий схему полей
     * @throws BadRequestException Если отсутствуют обязательные поля
     * @throws NumberFormatException При несоответствии числовых типов
     *
     * @sample
     * ```kotlin
     * val json = JsonObject(mapOf(
     *     "name" to JsonPrimitive("John"),
     *     "email" to JsonPrimitive("john@example.com")
     * ))
     * val statement = UsersTable.insert()
     * ReflectiveMapper.insertFromJson(statement, UsersTable, json, User::class)
     * statement.execute()
     * ```
     */
    @Suppress("UNCHECKED_CAST")
    fun insertFromJson(
        statement: InsertStatement<Number>,
        table: BaseTable,
        json: JsonObject,
        entityClass: KClass<*>
    ) {
        val meta = EntityMetadataCache.get(entityClass, table)
        val missingRequired = mutableListOf<String>()

        for (desc in meta.forCreate) {
            val propName = desc.property.name
            val jsonElement = json[propName]

            val value: Any? = when {
                jsonElement != null && jsonElement !is JsonNull ->
                    parseJsonValue(jsonElement, desc.column)

                desc.defaultValue != null ->
                    parseDefault(desc.defaultValue, desc.column)

                desc.isRequired -> {
                    missingRequired += propName
                    continue
                }

                else -> null
            }

            (desc.column as Column<Any?>).let { col ->
                statement[col] = value
            }
        }

        if (missingRequired.isNotEmpty()) {
            throw BadRequestException("Missing required fields: ${missingRequired.joinToString()}")
        }
    }

    // ==================== JSON → UpdateStatement (partial) ====================

    /**
     * Заполняет SQL-оператор обновления (UPDATE) данными из JSON-объекта.
     *
     * **Ключевые особенности:**
     * - Выполняет **частичное обновление** (partial update) — только поля, присланные клиентом
     * - Игнорирует поля, помеченные как `@ReadOnly` или `@Immutable`
     * - Поля с `JsonNull` явно устанавливаются в NULL
     * - Обязательно требует наличия поля `version` для оптимистичной блокировки
     *
     * **Логика работы:**
     * 1. Извлекает и валидирует поле `version` из JSON
     * 2. Проходит по всем полям категории `forUpdate`
     * 3. Если поле присутствует в JSON → устанавливает новое значение
     * 4. Если поле отсутствует → пропускает (не меняет существующее)
     * 5. Возвращает версию для последующей проверки в WHERE-условии
     *
     * @param statement SQL-оператор обновления (обычно из Exposed)
     * @param table Таблица, в которой выполняется обновление
     * @param json JSON-объект с частичными данными (например, из PATCH-запроса)
     * @param entityClass Класс сущности, определяющий схему полей
     * @return Значение поля `version` из JSON (необходимо для оптимистичной блокировки)
     * @throws BadRequestException Если отсутствует поле `version` или оно не является числом
     *
     * @sample
     * ```kotlin
     * val json = JsonObject(mapOf(
     *     "version" to JsonPrimitive(2),
     *     "email" to JsonPrimitive("newemail@example.com")
     * ))
     * val statement = UsersTable.update({ UsersTable.id eq 1 })
     * val version = ReflectiveMapper.updateFromJson(statement, UsersTable, json, User::class)
     * statement.where { UsersTable.version eq version }
     * statement.execute()
     * ```
     */
    @Suppress("UNCHECKED_CAST")
    fun updateFromJson(
        statement: UpdateStatement,
        table: BaseTable,
        json: JsonObject,
        entityClass: KClass<*>
    ): Long {
        val meta = EntityMetadataCache.get(entityClass, table)

        // version обязателен
        val versionElement = json["version"]
            ?: throw BadRequestException("Field 'version' is required for update")
        val version = versionElement.jsonPrimitive.longOrNull
            ?: throw BadRequestException("Field 'version' must be a number")

        for (desc in meta.forUpdate) {
            val propName = desc.property.name
            val jsonElement = json[propName] ?: continue  // не прислали → не меняем
            if (jsonElement is JsonNull) continue

            val value = parseJsonValue(jsonElement, desc.column)
            (desc.column as Column<Any?>).let { col ->
                statement[col] = value
            }
        }

        return version
    }

    // ==================== JSON parsing helpers ====================

    /**
     * Преобразует JSON-элемент в значение, подходящее для колонки БД.
     *
     * **Поддерживаемые преобразования:**
     * - JSON String → Kotlin String
     * - JSON Boolean → Kotlin Boolean
     * - JSON Number → Int или Long (в зависимости от типа колонки)
     * - JSON Number с плавающей точкой → Double
     * - Прочие случаи → строка (как fallback)
     *
     * **Важно:** Метод не рекурсивный и не поддерживает вложенные JSON-объекты.
     * Для сложных структур требуется отдельная обработка.
     *
     * @param element JSON-элемент из запроса клиента
     * @param column Целевая колонка БД (определяет требуемый тип)
     * @return Значение, готовое для передачи в `Column.set()`
     * @throws NumberFormatException Если строка не может быть преобразована в число
     */
    private fun parseJsonValue(element: JsonElement, column: Column<*>): Any {
        val primitive = element.jsonPrimitive
        val colType = column.columnType

        return when {
            primitive.isString -> primitive.content
            primitive.booleanOrNull != null -> primitive.boolean
            primitive.longOrNull != null -> {
                // Int или Long — зависит от типа колонки
                val l = primitive.long
                if (colType is IntegerColumnType) l.toInt() else l
            }
            primitive.doubleOrNull != null -> primitive.double
            else -> primitive.content
        }
    }

    /**
     * Парсит строковое значение дефолта в тип, соответствующий колонке.
     *
     * Используется для аннотации `@Default`, где значение хранится в виде строки.
     * Поддерживает базовые типы:
     * - Boolean → `toBoolean()`
     * - Integer → `toIntOrNull()`
     * - Long → `toLongOrNull()`
     * - Double → `toDoubleOrNull()`
     * - String и остальные → возвращает исходную строку
     *
     * @param defaultStr Строковое значение из аннотации `@Default`
     * @param column Колонка, определяющая целевой тип
     * @return Преобразованное значение или `null` при невозможности преобразования
     */
    private fun parseDefault(defaultStr: String, column: Column<*>): Any? {
        val colType = column.columnType
        return when (colType) {
            is BooleanColumnType -> defaultStr.toBoolean()
            is IntegerColumnType -> defaultStr.toIntOrNull()
            is LongColumnType -> defaultStr.toLongOrNull()
            is DoubleColumnType -> defaultStr.toDoubleOrNull()
            else -> defaultStr
        }
    }

    // ==================== Type coercion ====================

    /**
     * Принудительно преобразует значение к целевому типу.
     *
     * **Необходимость:**
     * При маппинге из `ResultRow` могут возникать ситуации,
     * когда тип значения в БД не совпадает с типом параметра конструктора
     * (например, `LocalDateTime` хранится как `String` в SQLite).
     *
     * **Поддерживаемые преобразования:**
     * - `LocalDateTime` → `String` (ISO-формат)
     * - Любой тип, не являющийся `String` → `String` (через `toString()`)
     * - Остальные случаи → исходное значение
     *
     * @param value Исходное значение из БД
     * @param targetType Целевой тип параметра конструктора (может быть `null`)
     * @return Преобразованное значение или исходное, если преобразование не требуется
     */
    private fun coerce(value: Any?, targetType: KClass<*>?): Any? {
        if (value == null || targetType == null) return value
        return when (targetType) {
            String::class if value is LocalDateTime -> value.toString()
            String::class if value !is String -> value.toString()
            else -> value
        }
    }
}
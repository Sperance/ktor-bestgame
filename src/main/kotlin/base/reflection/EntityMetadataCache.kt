package base.reflection

import base.annotations.ColumnName
import base.annotations.Immutable
import base.annotations.ReadOnly
import base.annotations.Required
import base.annotations.Unmapped
import base.table.BaseTable
import org.jetbrains.exposed.v1.core.Column
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.primaryConstructor

/**
 * Потокобезопасный кэш метаданных сущностей, построенных через рефлексию.
 *
 * Для каждой пары `(KClass сущности, KClass таблицы)` один раз вычисляет и сохраняет
 * описания свойств с их привязками к колонкам таблицы, параметрам конструктора
 * и аннотациям. Повторные обращения возвращают результат из кэша без повторного
 * сканирования через рефлексию.
 */
object EntityMetadataCache {

    /**
     * Описание одного свойства сущности с его привязками и метаданными.
     *
     * @property property Ссылка на свойство сущности через рефлексию.
     * @property column Колонка таблицы [BaseTable], к которой привязано свойство.
     * @property constructorParam Соответствующий параметр первичного конструктора сущности,
     *   или `null`, если свойство не входит в конструктор.
     * @property isReadOnly `true`, если свойство помечено аннотацией [ReadOnly].
     *   Такие поля исключаются из INSERT и UPDATE (например, `created_at`, управляемый БД).
     * @property isImmutable `true`, если свойство помечено аннотацией [Immutable].
     *   Поле включается в INSERT, но исключается из UPDATE (например, внешний ключ-владелец).
     * @property isRequired `true`, если поле обязательно для передачи при создании сущности:
     *   явно помечено [Required]
     */
    data class PropDescriptor(
        val property: KProperty1<out Any, *>,
        val column: Column<*>,
        val constructorParam: KParameter?,
        val isReadOnly: Boolean,
        val isImmutable: Boolean,
        val isRequired: Boolean
    )

    /**
     * Предвычисленные срезы дескрипторов свойств сущности для различных операций.
     *
     * @property all Все дескрипторы сущности, включая [ReadOnly] и [Immutable]-поля.
     * @property forConstructor Дескрипторы, у которых есть соответствующий параметр
     *   первичного конструктора. Используется при маппинге строки БД в объект (`toEntity`).
     * @property forCreate Дескрипторы, допустимые для INSERT: всё кроме [ReadOnly]-полей.
     * @property forUpdate Дескрипторы, допустимые для UPDATE: всё кроме [ReadOnly]
     *   и [Immutable]-полей.
     * @property constructorParams Параметры первичного конструктора сущности,
     *   индексированные по имени, для быстрого поиска при сборке объекта.
     */
    data class Metadata(
        val all: List<PropDescriptor>,
        val forConstructor: List<PropDescriptor>,
        val forCreate: List<PropDescriptor>,
        val forUpdate: List<PropDescriptor>,
        val constructorParams: Map<String, KParameter>,
        /** Экземпляр сущности, созданный с дефолтами конструктора. Используется для извлечения Kotlin-дефолтов. */
        val defaultInstance: Any?
    )

    /**
     * Внутреннее хранилище кэша.
     * Ключ — пара `(KClass сущности, KClass таблицы)`, значение — вычисленные [Metadata].
     * [ConcurrentHashMap] обеспечивает безопасность при параллельных обращениях.
     */
    private val cache = ConcurrentHashMap<Pair<KClass<*>, KClass<*>>, Metadata>()

    /**
     * Возвращает [Metadata] для заданной пары сущность–таблица.
     *
     * При первом обращении метаданные вычисляются через [build] и сохраняются в кэш.
     * При последующих — возвращаются из кэша без рефлексии.
     *
     * @param E Тип сущности.
     * @param T Тип таблицы, наследника [BaseTable].
     * @param entityClass [KClass] сущности.
     * @param table Экземпляр таблицы, используемый для поиска колонок.
     * @return Предвычисленные метаданные для данной пары.
     */
    fun <E : Any, T : BaseTable> get(entityClass: KClass<E>, table: T): Metadata {
        val key = entityClass to table::class
        return cache.getOrPut(key) { build(entityClass, table) }
    }

    /**
     * Строит [Metadata] для сущности [entityClass] относительно таблицы [table].
     *
     * Алгоритм:
     * 1. Получает первичный конструктор сущности (обязателен).
     * 2. Индексирует параметры конструктора по имени.
     * 3. Перебирает объявленные свойства сущности:
     *    - пропускает помеченные [Unmapped];
     *    - резолвит колонку через [resolveColumn] (пропускает, если не найдена);
     *    - считывает аннотации [ReadOnly], [Immutable], [Required];
     *    - вычисляет признак обязательности поля.
     * 4. Формирует срезы дескрипторов для конструктора, INSERT и UPDATE.
     *
     * @param E Тип сущности.
     * @param entityClass [KClass] сущности.
     * @param table Таблица для резолва колонок.
     * @return Готовый объект [Metadata].
     * @throws IllegalArgumentException Если у [entityClass] отсутствует первичный конструктор.
     */
    private fun <E : Any> build(entityClass: KClass<E>, table: BaseTable): Metadata {
        val constructor = entityClass.primaryConstructor
            ?: error("${entityClass.simpleName} must have a primary constructor")

        val paramsByName = constructor.parameters
            .filter { it.name != null }
            .associateBy { it.name!! }

        val descriptors = entityClass.declaredMemberProperties.mapNotNull { prop ->
            if (prop.hasAnnotation<Unmapped>()) return@mapNotNull null

            val column = resolveColumn(table, prop) ?: return@mapNotNull null

            val isReadOnly = prop.hasAnnotation<ReadOnly>()
            val isImmutable = prop.hasAnnotation<Immutable>()
            val isRequired = prop.hasAnnotation<Required>() ||
                    (!isReadOnly && paramsByName[prop.name]?.type?.isMarkedNullable == false
                            && paramsByName[prop.name]?.isOptional == false)

            PropDescriptor(
                property = prop,
                column = column,
                constructorParam = paramsByName[prop.name],
                isReadOnly = isReadOnly,
                isImmutable = isImmutable,
                isRequired = isRequired
            )
        }

        // Создаём экземпляр класса, передавая только обязательные параметры как null/0/false.
        // Нужен для извлечения Kotlin-дефолтов (isActive = false и т.п.)
        val defaultInstance: Any? = try {
            val args = constructor.parameters
                .filter { !it.isOptional }
                .associateWith { param ->
                    if (param.type.isMarkedNullable) null
                    else when (param.type.classifier) {
                        String::class  -> ""
                        Int::class     -> 0
                        Long::class    -> 0L
                        Double::class  -> 0.0
                        Float::class   -> 0f
                        Boolean::class -> false
                        Short::class   -> 0.toShort()
                        Byte::class    -> 0.toByte()
                        ULong::class   -> 0u
                        else           -> null
                    }
                }
            constructor.callBy(args)
        } catch (_: Exception) {
            null
        }

        return Metadata(
            all = descriptors,
            forConstructor = descriptors.filter { it.constructorParam != null },
            forCreate = descriptors.filter { !it.isReadOnly },
            forUpdate = descriptors.filter { !it.isReadOnly && !it.isImmutable },
            constructorParams = paramsByName,
            defaultInstance = defaultInstance
        )
    }

    /**
     * Находит колонку таблицы [table], соответствующую свойству [prop].
     *
     * Приоритет имени колонки:
     * 1. Значение аннотации [ColumnName], если она присутствует на свойстве.
     * 2. Автоматически сконвертированное имя свойства: `camelCase` → `snake_case` через [camelToSnake].
     *
     * @param table Таблица, в колонках которой ведётся поиск.
     * @param prop Свойство сущности.
     * @return Найденная [Column], или `null`, если колонка с таким именем отсутствует в таблице.
     */
    private fun resolveColumn(table: BaseTable, prop: KProperty1<*, *>): Column<*>? {
        val colName = prop.findAnnotation<ColumnName>()?.value ?: camelToSnake(prop.name)
        return table.columns.find { it.name == colName }
    }

    /**
     * Конвертирует строку из `camelCase` в `snake_case`.
     *
     * Каждая заглавная буква заменяется на строчную с предшествующим символом `_`,
     * кроме первого символа строки.
     *
     * Примеры:
     * - `"userId"` → `"user_id"`
     * - `"createdAt"` → `"created_at"`
     * - `"id"` → `"id"`
     *
     * @param name Исходная строка в `camelCase`.
     * @return Строка в формате `snake_case`.
     */
    internal fun camelToSnake(name: String): String = buildString {
        for ((i, ch) in name.withIndex()) {
            if (ch.isUpperCase()) {
                if (i > 0) append('_')
                append(ch.lowercaseChar())
            } else {
                append(ch)
            }
        }
    }
}
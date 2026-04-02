package base.reflection

import base.annotations.ColumnName
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
 * Потокобезопасный кэш метаданных рефлексии.
 *
 * Ключ кэша — пара (KClass<DTO>, KClass<Table>), потому что
 * один и тот же DTO теоретически может маппиться в разные таблицы.
 *
 * Вычисляется один раз при первом обращении, далее мгновенный lookup.
 */
object EntityMetadataCache {

    /**
     * Закэшированные метаданные для пары (Entity, Table).
     */
    data class Metadata(
        /** Все привязки свойство ↔ колонка */
        val descriptors: List<PropertyDescriptor>,
        /** Только привязки, у которых есть параметр конструктора (для fromRow) */
        val constructorDescriptors: List<PropertyDescriptor>,
        /** Привязки для INSERT/UPDATE (без id, version, createdAt, updatedAt) */
        val mutableDescriptors: List<PropertyDescriptor>,
        /** Маппинг paramName → KParameter (для вызова конструктора) */
        val constructorParams: Map<String, KParameter>
    )

    // Поля, которыми управляет BaseRepository, а не пользовательский DTO
    private val MANAGED_FIELDS = setOf("id", "version", "createdAt", "updatedAt")

    private val cache = ConcurrentHashMap<Pair<KClass<*>, KClass<*>>, Metadata>()

    /**
     * Получить (или вычислить и закэшировать) метаданные.
     */
    fun <E : Any, T : BaseTable> get(entityClass: KClass<E>, table: T): Metadata {
        val key = entityClass to table::class
        return cache.getOrPut(key) { buildMetadata(entityClass, table) }
    }

    // ==================== Построение метаданных ====================

    private fun <E : Any> buildMetadata(entityClass: KClass<E>, table: BaseTable): Metadata {
        val constructor = entityClass.primaryConstructor
            ?: error("${entityClass.simpleName} must have a primary constructor")

        val paramsByName: Map<String, KParameter> = constructor.parameters
            .filter { it.name != null }
            .associateBy { it.name!! }

        // Все Kotlin-свойства DTO (объявленные именно в этом классе)
        val props = entityClass.declaredMemberProperties

        // Строим привязки
        val descriptors = props.mapNotNull { prop ->
            if (prop.hasAnnotation<Unmapped>()) return@mapNotNull null

            val column = resolveColumn(table, prop) ?: return@mapNotNull null
            val param = paramsByName[prop.name]

            PropertyDescriptor(
                property = prop,
                column = column,
                constructorParam = param
            )
        }

        val constructorDescriptors = descriptors.filter { it.constructorParam != null }
        val mutableDescriptors = descriptors.filter { it.property.name !in MANAGED_FIELDS }

        return Metadata(
            descriptors = descriptors,
            constructorDescriptors = constructorDescriptors,
            mutableDescriptors = mutableDescriptors,
            constructorParams = paramsByName
        )
    }

    // ==================== Резолв колонки ====================

    private fun resolveColumn(table: BaseTable, prop: KProperty1<*, *>): Column<*>? {
        val colName = prop.findAnnotation<ColumnName>()?.value
            ?: camelToSnake(prop.name)
        return table.columns.find { it.name == colName }
    }

    /**
     * camelCase → snake_case
     * authorId → author_id, isPublished → is_published
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
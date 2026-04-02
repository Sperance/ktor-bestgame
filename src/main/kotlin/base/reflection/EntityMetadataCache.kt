package base.reflection

import base.annotations.ColumnName
import base.annotations.DefaultValue
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

object EntityMetadataCache {

    data class PropDescriptor(
        val property: KProperty1<out Any, *>,
        val column: Column<*>,
        val constructorParam: KParameter?,
        val isReadOnly: Boolean,
        val isImmutable: Boolean,
        val defaultValue: String?,
        val isRequired: Boolean
    )

    data class Metadata(
        /** Все привязки */
        val all: List<PropDescriptor>,
        /** Для конструктора (toEntity) */
        val forConstructor: List<PropDescriptor>,
        /** Для INSERT (не ReadOnly) */
        val forCreate: List<PropDescriptor>,
        /** Для UPDATE (не ReadOnly, не Immutable) */
        val forUpdate: List<PropDescriptor>,
        /** Параметры конструктора по имени */
        val constructorParams: Map<String, KParameter>
    )

    private val cache = ConcurrentHashMap<Pair<KClass<*>, KClass<*>>, Metadata>()

    fun <E : Any, T : BaseTable> get(entityClass: KClass<E>, table: T): Metadata {
        val key = entityClass to table::class
        return cache.getOrPut(key) { build(entityClass, table) }
    }

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
            val defaultStr = prop.findAnnotation<DefaultValue>()?.value
            val isRequired = prop.hasAnnotation<Required>() ||
                    (!isReadOnly && paramsByName[prop.name]?.type?.isMarkedNullable == false && defaultStr == null
                            && paramsByName[prop.name]?.isOptional == false)

            PropDescriptor(
                property = prop,
                column = column,
                constructorParam = paramsByName[prop.name],
                isReadOnly = isReadOnly,
                isImmutable = isImmutable,
                defaultValue = defaultStr,
                isRequired = isRequired
            )
        }

        return Metadata(
            all = descriptors,
            forConstructor = descriptors.filter { it.constructorParam != null },
            forCreate = descriptors.filter { !it.isReadOnly },
            forUpdate = descriptors.filter { !it.isReadOnly && !it.isImmutable },
            constructorParams = paramsByName
        )
    }

    private fun resolveColumn(table: BaseTable, prop: KProperty1<*, *>): Column<*>? {
        val colName = prop.findAnnotation<ColumnName>()?.value ?: camelToSnake(prop.name)
        return table.columns.find { it.name == colName }
    }

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
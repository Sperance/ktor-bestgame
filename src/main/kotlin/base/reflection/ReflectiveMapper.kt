package base.reflection

import base.table.BaseTable
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.statements.InsertStatement
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import java.time.LocalDateTime
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.primaryConstructor

/**
 * Stateless-маппер: все тяжёлые вычисления — в [EntityMetadataCache].
 * Здесь только тонкая логика чтения/записи значений.
 */
object ReflectiveMapper {

    // ==================== ResultRow → Entity ====================

    fun <E : Any> toEntity(klass: KClass<E>, table: BaseTable, row: ResultRow): E {
        val meta = EntityMetadataCache.get(klass, table)
        val constructor = klass.primaryConstructor!!

        val args = meta.constructorDescriptors.associate { desc ->
            val rawValue = row[desc.column]
            val converted = coerce(rawValue, desc.constructorParam!!.type.classifier as? KClass<*>)
            desc.constructorParam to converted
        }

        // Параметры с default-значениями, которых нет в таблице — просто не передаём,
        // Kotlin сам подставит default.
        return constructor.callBy(args)
    }

    // ==================== DTO → InsertStatement ====================

    @Suppress("UNCHECKED_CAST")
    fun insertFromDto(statement: InsertStatement<Number>, table: BaseTable, dto: Any) {
        val meta = EntityMetadataCache.get(dto::class, table)
        for (desc in meta.mutableDescriptors) {
            val value = (desc.property as KProperty1<Any, *>).get(dto)
            (desc.column as Column<Any?>).let { col ->
                statement[col] = value
            }
        }
    }

    // ==================== DTO → UpdateStatement (partial: null = skip) ====================

    @Suppress("UNCHECKED_CAST")
    fun partialUpdateFromDto(statement: UpdateStatement, table: BaseTable, dto: Any) {
        val meta = EntityMetadataCache.get(dto::class, table)
        for (desc in meta.mutableDescriptors) {
            val value = (desc.property as KProperty1<Any, *>).get(dto)
            // Partial update: null означает «не менять»
            if (value != null) {
                (desc.column as Column<Any?>).let { col ->
                    statement[col] = value
                }
            }
        }
    }

    // ==================== DTO → UpdateStatement (full: null = записать null) ====================

    @Suppress("UNCHECKED_CAST")
    fun fullUpdateFromDto(statement: UpdateStatement, table: BaseTable, dto: Any) {
        val meta = EntityMetadataCache.get(dto::class, table)
        for (desc in meta.mutableDescriptors) {
            val value = (desc.property as KProperty1<Any, *>).get(dto)
            (desc.column as Column<Any?>).let { col ->
                statement[col] = value
            }
        }
    }

    // ==================== Type coercion ====================

    /**
     * Exposed возвращает типы вроде LocalDateTime, а DTO может хотеть String.
     * Расширяется по необходимости.
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
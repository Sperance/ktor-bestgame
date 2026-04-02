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

object ReflectiveMapper {

    // ==================== ResultRow → Entity ====================

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
     * Принимает сырой JsonObject от клиента.
     * Берёт только поля из forCreate (без ReadOnly).
     * Валидирует required-поля.
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
     * Partial update: только те поля, которые клиент прислал в JSON.
     * ReadOnly и Immutable — игнорируются.
     * Возвращает version из JSON (для OL).
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

    private fun coerce(value: Any?, targetType: KClass<*>?): Any? {
        if (value == null || targetType == null) return value
        return when (targetType) {
            String::class if value is LocalDateTime -> value.toString()
            String::class if value !is String -> value.toString()
            else -> value
        }
    }
}
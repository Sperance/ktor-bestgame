package base.repository

import CONST_SYSTEM_FIELDS
import base.entity.StockEntity
import base.entity.TrackedEntity
import com.mongodb.client.model.Updates
import org.bson.BsonDocument
import org.bson.BsonDocumentReader
import org.bson.BsonDocumentWriter
import org.bson.BsonValue
import org.bson.Document
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.bson.codecs.configuration.CodecRegistry
import org.bson.conversions.Bson
import kotlin.reflect.KClass

/**
 * Документ сущности (1.63.0, вынесено из [BaseRepository]): кодирование кодеком коллекции, память прочитанного
 * ([TrackedEntity.loaded]), разница для `$set`/`$unset` и наложение частичной правки. [stored] - хук репозитория:
 * убирает из документа то, что лежит не в этой коллекции (у героя - заход, см. `HeroRunStore`).
 */
class EntityCodec<T : StockEntity>(
    private val entityClass: KClass<T>,
    private val registry: () -> CodecRegistry,
    private val managedFields: () -> Set<String>,
    private val stored: (BsonDocument) -> Unit = {},
) {
    private val codec get() = registry().get(entityClass.java)

    /** Поля сущности для записи: документ кодеком коллекции минус системные, управляемые базой и чужие поля. */
    fun encode(entity: T): Map<String, BsonValue> {
        val document = BsonDocument()
        codec.encode(BsonDocumentWriter(document), entity, EncoderContext.builder().build())
        stored(document)
        val managed = managedFields()
        return document.filterKeys { it !in CONST_SYSTEM_FIELDS && it !in managed }
    }

    /** Сущность, которая помнит документ, запоминает его, каким прочла. */
    fun tracked(entity: T): T = entity.also { if (it is TrackedEntity) it.loaded = encode(it) }

    /** Что пишется: изменившиеся с чтения поля и обнулённые в памяти (кодек не пишет null - поле иначе осталось бы в базе). */
    fun changes(entity: T, encoded: Map<String, BsonValue>): List<Bson> {
        val loaded = (entity as? TrackedEntity)?.loaded
        val managed = managedFields()
        val cleared = fieldNames(entity).filterNot { it in encoded || it in CONST_SYSTEM_FIELDS || it in managed || (loaded != null && it !in loaded) }
        val changed = if (loaded == null) encoded else encoded.filter { (field, value) -> loaded[field] != value }
        return changed.map { (field, value) -> Updates.set(field, value) } + cleared.map { Updates.unset(it) }
    }

    /** Копия [entity] с полями [fields], закодированными кодеком коллекции - как их записал бы `$set`; память чтения - та же. */
    fun patched(entity: T, fields: Map<String, Any?>): T {
        val document = BsonDocument()
        codec.encode(BsonDocumentWriter(document), entity, EncoderContext.builder().build())
        document.putAll(Document(fields).toBsonDocument(Document::class.java, registry()))
        val copy = codec.decode(BsonDocumentReader(document), DecoderContext.builder().build())
        if (entity is TrackedEntity && copy is TrackedEntity) copy.loaded = entity.loaded
        return copy
    }

    /** Имена всех сериализуемых полей конкретного класса сущности, как они лежат в документе. */
    private fun fieldNames(entity: T): List<String> = kotlinx.serialization.serializer(entity.javaClass).descriptor.let { descriptor -> List(descriptor.elementsCount, descriptor::getElementName) }
}

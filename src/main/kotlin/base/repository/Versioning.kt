package base.repository

import CONST_FIELD_ID
import CONST_FIELD_UPDATED
import CONST_FIELD_VERSION
import base.entity.StockEntity
import base.entity.VersionedEntity
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import extensions.now
import kotlinx.datetime.LocalDateTime
import org.bson.conversions.Bson

/** Оптимистичная блокировка (1.63.0, вынесено из [BaseRepository]): фильтр записи по версии и служебные поля новой версии. */
internal object Versioning {
    fun byId(id: String): Bson = Filters.eq(CONST_FIELD_ID, id)

    /** Документ по id и, у версионируемой записи, по версии, которую держит объект в памяти. */
    fun identity(entity: StockEntity): Bson = if (entity is VersionedEntity) {
        Filters.and(byId(entity._id), Filters.eq(CONST_FIELD_VERSION, entity.version))
    } else {
        byId(entity._id)
    }

    /** Служебные поля следующей версии: номер и время правки. */
    fun bump(entity: StockEntity): List<Bson> = if (entity is VersionedEntity) {
        listOf(Updates.set(CONST_FIELD_VERSION, entity.version + 1), Updates.set(CONST_FIELD_UPDATED, LocalDateTime.now()))
    } else {
        emptyList()
    }

    fun versionOf(entity: StockEntity): Long = (entity as? VersionedEntity)?.version ?: 0L
}

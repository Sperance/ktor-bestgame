package ru.descend.features.character.persistence

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.ClientSession
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.bson.conversions.Bson
import ru.descend.features.character.model.CharacterEquipmentItem
import ru.descend.features.character.model.CharacterEquipments
import ru.descend.infrastructure.mongo.BaseRepository
import ru.descend.shared.CONST_FIELD_ID
import ru.descend.shared.extensions.now
import ru.descend.shared.http.conflict
import ru.descend.shared.http.missing

/**
 * Инвентарь экипировки: одна запись на предмет.
 *
 * Все чтения ограничены либо конкретными uuid (надетое, предмет команды), либо страницей,
 * поэтому стоимость операции не зависит от размера инвентаря.
 */
class CharacterEquipmentRepository : BaseRepository<CharacterEquipmentItem>(
    entityClass = CharacterEquipmentItem::class
) {
    companion object {
        const val FIELD_CHARACTER = "characterId"

        /** Страница инвентаря не должна превращаться в выгрузку миллиона предметов. */
        const val MAX_PAGE_SIZE = 200
        const val DEFAULT_PAGE_SIZE = 50
    }

    init {
        initialize()
        runBlocking {
            // Курсорная выборка `characterId + _id` полностью покрывается индексом.
            runCatching { collection.createIndex(Indexes.ascending(FIELD_CHARACTER, CONST_FIELD_ID)) }
            // Экземпляры одного шаблона: нужны миграции ссылок на определения модификаторов.
            runCatching { collection.createIndex(Indexes.ascending("item.equipmentId", CONST_FIELD_ID)) }
        }
    }

    private fun owned(characterId: String): Bson = Filters.eq(FIELD_CHARACTER, characterId)

    suspend fun count(characterId: String): Long = collection.countDocuments(owned(characterId))

    /** Курсорная страница: `after` — uuid последнего предмета предыдущей страницы. */
    suspend fun page(characterId: String, size: Int, after: String? = null): List<CharacterEquipments> {
        require(size in 1..MAX_PAGE_SIZE) { "Invalid inventory page size" }
        val filter = if (after == null) owned(characterId)
        else Filters.and(owned(characterId), Filters.gt(CONST_FIELD_ID, after))
        return collection.find(filter).sort(Sorts.ascending(CONST_FIELD_ID)).limit(size).toList().map { it.item }
    }

    suspend fun byUuids(characterId: String, uuids: Collection<String>, session: ClientSession? = null): List<CharacterEquipments> {
        if (uuids.isEmpty()) return emptyList()
        val filter = Filters.and(owned(characterId), Filters.`in`(CONST_FIELD_ID, uuids.toSet()))
        val rows = if (session == null) collection.find(filter) else collection.find(session, filter)
        return rows.sort(Sorts.ascending(CONST_FIELD_ID)).toList().map { it.item }
    }

    suspend fun byUuid(characterId: String, uuid: String, session: ClientSession? = null): CharacterEquipments? {
        val filter = Filters.and(owned(characterId), Filters.eq(CONST_FIELD_ID, uuid))
        val rows = if (session == null) collection.find(filter) else collection.find(session, filter)
        return rows.firstOrNull()?.item
    }

    suspend fun add(characterId: String, item: CharacterEquipments, session: ClientSession) =
        addAll(characterId, listOf(item), session)

    suspend fun addAll(characterId: String, items: List<CharacterEquipments>, session: ClientSession) {
        if (items.isEmpty()) return
        require(items.map { it.uuid }.toSet().size == items.size) { "Duplicate equipment uuid" }
        collection.insertMany(session, items.map { CharacterEquipmentItem(characterId, it) })
    }

    /** Замена состояния предмета на месте: uuid и владелец не меняются. */
    suspend fun replace(characterId: String, item: CharacterEquipments, session: ClientSession) {
        val filter = Filters.and(owned(characterId), Filters.eq(CONST_FIELD_ID, item.uuid))
        val existing = collection.find(session, filter).firstOrNull() ?: missing()
        val result = collection.replaceOne(session, filter,
            existing.copy(item = item, version = existing.version + 1, updatedAt = LocalDateTime.now()))
        if (result.matchedCount == 0L) conflict()
    }
}

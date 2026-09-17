package ru.descend.features.character.persistence

import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Accumulators
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Projections
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.ClientSession
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.bson.Document
import org.bson.conversions.Bson
import ru.descend.features.character.model.CharacterInventoryItem
import ru.descend.infrastructure.mongo.BaseRepository
import ru.descend.shared.CONST_FIELD_ID
import ru.descend.shared.http.invalid

/**
 * Предметы персонажа без стаков: строка на каждую принадлежащую единицу.
 *
 * Цена операций — прямое следствие отказа от стаков: выдача и списание стоят ровно столько
 * документов, сколько единиц затронуто, а количество считается по индексу. Поэтому одна операция
 * ограничена [MAX_UNITS_PER_COMMAND] единицами.
 */
class CharacterInventoryRepository : BaseRepository<CharacterInventoryItem>(
    entityClass = CharacterInventoryItem::class
) {
    companion object {
        const val FIELD_CHARACTER = "characterId"
        const val FIELD_ITEM = "itemId"

        /**
         * Сколько единиц предметов одна команда может создать или уничтожить.
         *
         * Это прямая цена отказа от стаков: каждая единица — документ, и все они пишутся в одной
         * транзакции с обновлением персонажа. Прежняя схема позволяла выдать миллиард одним
         * числом в поле `amount`; теперь миллиард — это миллиард документов, поэтому выдача
         * дробится на команды.
         */
        const val MAX_UNITS_PER_COMMAND = 10_000L

        /** Размер пачки вставки/удаления внутри одной команды. */
        const val WRITE_BATCH = 1_000
        const val MAX_PAGE_SIZE = 200
        const val DEFAULT_PAGE_SIZE = 50
    }

    init {
        initialize()
        // `(characterId, itemId, _id)` покрывает и баланс, и выбор единиц под списание, и страницу.
        runBlocking { runCatching { collection.createIndex(Indexes.ascending(FIELD_CHARACTER, FIELD_ITEM, CONST_FIELD_ID)) } }
    }

    private fun owned(characterId: String): Bson = Filters.eq(FIELD_CHARACTER, characterId)
    private fun owned(characterId: String, itemId: String): Bson =
        Filters.and(owned(characterId), Filters.eq(FIELD_ITEM, itemId))

    fun checkedAmount(amount: Long): Long {
        if (amount !in 1..MAX_UNITS_PER_COMMAND) invalid("One command handles 1 to $MAX_UNITS_PER_COMMAND item units")
        return amount
    }

    /** Сколько всего единиц предметов у персонажа, независимо от типа. */
    suspend fun total(characterId: String): Long = collection.countDocuments(owned(characterId))

    suspend fun count(characterId: String, itemId: String, session: ClientSession? = null): Long {
        val filter = owned(characterId, itemId)
        return if (session == null) collection.countDocuments(filter) else collection.countDocuments(session, filter)
    }

    /**
     * Сколько единиц каждого типа у персонажа. Значение производное, а не хранимое:
     * без стаков его нельзя узнать иначе, чем пересчитав принадлежащие единицы.
     */
    suspend fun totals(characterId: String, session: ClientSession? = null): Map<String, Long> {
        val pipeline = listOf(Aggregates.match(owned(characterId)),
            Aggregates.group("\$$FIELD_ITEM", Accumulators.sum("units", 1L)))
        val rows = collection.withDocumentClass(Document::class.java).let {
            if (session == null) it.aggregate(pipeline) else it.aggregate(session, pipeline)
        }.toList()
        return rows.associate { it.getString("_id") to it.get("units", Number::class.java).toLong() }
    }

    /** Курсорная страница: `after` — `_id` последней единицы предыдущей страницы. */
    suspend fun page(characterId: String, size: Int, after: String? = null): List<CharacterInventoryItem> {
        require(size in 1..MAX_PAGE_SIZE) { "Invalid inventory page size" }
        val filter = if (after == null) owned(characterId)
        else Filters.and(owned(characterId), Filters.gt(CONST_FIELD_ID, after))
        return collection.find(filter).sort(Sorts.ascending(CONST_FIELD_ID)).limit(size).toList()
    }

    suspend fun grant(characterId: String, itemId: String, amount: Long, session: ClientSession) {
        var remaining = checkedAmount(amount)
        while (remaining > 0) {
            val batch = minOf(remaining, WRITE_BATCH.toLong()).toInt()
            collection.insertMany(session, (1..batch).map { CharacterInventoryItem(characterId, itemId) })
            remaining -= batch
        }
    }

    /** Списывает ровно [amount] единиц; если их меньше — операция отклоняется целиком. */
    suspend fun consume(characterId: String, itemId: String, amount: Long, session: ClientSession) {
        val needed = checkedAmount(amount)
        // Проекция только по _id: единица предмета ничего, кроме владельца и типа, не несёт.
        val ids = collection.withDocumentClass(Document::class.java).find(session, owned(characterId, itemId))
            .projection(Projections.include(CONST_FIELD_ID)).sort(Sorts.ascending(CONST_FIELD_ID))
            .limit(needed.toInt()).toList().map { it.getString(CONST_FIELD_ID) }
        if (ids.size.toLong() != needed) invalid("Not enough items")
        ids.chunked(WRITE_BATCH).forEach { collection.deleteMany(session, Filters.`in`(CONST_FIELD_ID, it)) }
    }
}

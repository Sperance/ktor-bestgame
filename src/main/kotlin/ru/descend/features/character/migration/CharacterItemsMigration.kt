package ru.descend.features.character.migration

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Projections
import com.mongodb.client.model.Updates
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import ru.descend.features.character.persistence.CharacterInventoryRepository
import ru.descend.features.character.persistence.CharacterRepository
import ru.descend.infrastructure.mongo.MongoFactory
import ru.descend.infrastructure.mongo.MongoFactory.transactionExecute
import ru.descend.shared.CONST_FIELD_ID
import ru.descend.shared.extensions.printLog

/** Проекция старой схемы: стака `{itemId, amount}` в текущей модели больше нет. */
@Serializable
internal data class LegacyStack(val itemId: String, val amount: Long)

@Serializable
internal data class LegacyStacks(val _id: String, val items: List<LegacyStack> = emptyList())

/**
 * Разворачивает старые стаки `Character.items` в отдельные документы — по одному на единицу.
 *
 * Стак `{itemId, amount: 7}` превращается в семь документов `CharacterInventoryItem`. Миграция
 * идемпотентна и возобновляема: для каждой пары (персонаж, тип предмета) она досоздаёт недостающее
 * до нужного количества, поэтому прерванный запуск дописывает остаток, а повторный не делает ничего.
 * Массив снимается с персонажа только после того, как все его типы развёрнуты.
 *
 * Стак больше [MAX_UNITS_PER_STACK] единиц миграция не разворачивает молча: она останавливается с
 * понятной ошибкой, чтобы такое количество разобрал человек, а не превратил старт сервера в
 * многочасовую вставку.
 */
class CharacterItemsMigration(private val characters: CharacterRepository,
    private val inventory: CharacterInventoryRepository) {

    private val legacy = MongoFactory.getDatabase().getCollection("Character", LegacyStacks::class.java)
    private val pendingFilter = Filters.and(Filters.exists(FIELD, true), Filters.ne(FIELD, emptyList<Any>()))

    suspend fun migrate() {
        val pending = legacy.find(pendingFilter).projection(Projections.include(CONST_FIELD_ID)).toList().map { it._id }
        if (pending.isEmpty()) return
        printLog("[MIGRATION::CharacterInventoryItem] персонажей к переносу: ${pending.size}")
        for (id in pending) {
            val owner = legacy.find(Filters.and(Filters.eq(CONST_FIELD_ID, id), pendingFilter))
                .projection(Projections.include(CONST_FIELD_ID, FIELD)).firstOrNull() ?: continue
            val stacks = owner.items.filter { it.amount > 0 }.groupBy { it.itemId }
                .mapValues { (_, rows) -> rows.sumOf { it.amount } }
            stacks.forEach { (itemId, amount) ->
                require(amount <= MAX_UNITS_PER_STACK) {
                    "Character $id holds $amount units of $itemId; expand it manually before starting with the stack-free schema"
                }
                expand(id, itemId, amount)
            }
            // Массив снимается последним: пока он на месте, прерванная миграция возобновляема.
            transactionExecute("character.items.migrate.finish", retryTransientErrors = true) { session ->
                characters.collection.updateOne(session, Filters.eq(CONST_FIELD_ID, id), Updates.unset(FIELD))
            }
        }
        printLog("[MIGRATION::CharacterInventoryItem] перенос завершён")
    }

    /** Досоздаёт единицы до [amount] штук; уже созданные повторно не вставляются. */
    private suspend fun expand(characterId: String, itemId: String, amount: Long) {
        while (true) {
            val missing = amount - inventory.count(characterId, itemId)
            if (missing <= 0) return
            val batch = minOf(missing, CharacterInventoryRepository.WRITE_BATCH.toLong())
            transactionExecute("character.items.migrate", retryTransientErrors = true) { session ->
                inventory.grant(characterId, itemId, batch, session)
            }
        }
    }

    private companion object {
        const val FIELD = "items"
        const val MAX_UNITS_PER_STACK = 100_000L
    }
}

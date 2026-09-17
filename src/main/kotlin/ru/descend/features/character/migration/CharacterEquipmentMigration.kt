package ru.descend.features.character.migration

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Projections
import com.mongodb.client.model.Updates
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import ru.descend.features.character.model.CharacterEquipments
import ru.descend.features.character.persistence.CharacterEquipmentRepository
import ru.descend.features.character.persistence.CharacterRepository
import ru.descend.infrastructure.mongo.MongoFactory
import ru.descend.infrastructure.mongo.MongoFactory.transactionExecute
import ru.descend.shared.CONST_FIELD_ID
import ru.descend.shared.extensions.printLog

/** Проекция старой схемы: в текущей модели у Character поля `equipments` уже нет. */
@Serializable
internal data class LegacyInventory(val _id: String, val equipments: List<CharacterEquipments> = emptyList())

/**
 * Переносит инвентарь из массива `Character.equipments` в коллекцию `CharacterEquipmentItem`.
 *
 * Старая схема держала все предметы внутри документа персонажа, и уже несколько предметов
 * со снимком базы и роллами делали его тяжёлым при лимите Mongo в 16 МБ. Миграция идемпотентна:
 * предмет с существующим uuid пропускается, а массив снимается с персонажа в той же транзакции,
 * что и вставка, поэтому повторный запуск (и запуск на уже мигрированной базе) ничего не делает.
 *
 * Персонажи обрабатываются по одному: миграция сама не поднимает в память больше одного инвентаря.
 */
class CharacterEquipmentMigration(private val characters: CharacterRepository,
    private val equipmentItems: CharacterEquipmentRepository) {

    private val legacy = MongoFactory.getDatabase().getCollection("Character", LegacyInventory::class.java)
    private val pendingFilter = Filters.and(Filters.exists(FIELD, true), Filters.ne(FIELD, emptyList<Any>()))

    suspend fun migrate() {
        // Сначала только идентификаторы: сами массивы читаются по одному внутри транзакции.
        val pending = legacy.find(pendingFilter).projection(Projections.include(CONST_FIELD_ID)).toList().map { it._id }
        if (pending.isEmpty()) return
        printLog("[MIGRATION::CharacterEquipmentItem] персонажей к переносу: ${pending.size}")
        for (id in pending) transactionExecute("character.equipments.migrate", retryTransientErrors = true) { session ->
            val owner = legacy.find(session, Filters.and(Filters.eq(CONST_FIELD_ID, id), pendingFilter))
                .projection(Projections.include(CONST_FIELD_ID, FIELD)).firstOrNull()
            if (owner != null) {
                val items = owner.equipments.distinctBy { it.uuid }
                val existing = equipmentItems.byUuids(id, items.map { it.uuid }, session).map { it.uuid }.toSet()
                equipmentItems.addAll(id, items.filter { it.uuid !in existing }, session)
                characters.collection.updateOne(session, Filters.eq(CONST_FIELD_ID, id), Updates.unset(FIELD))
            }
        }
        printLog("[MIGRATION::CharacterEquipmentItem] перенос завершён")
    }

    private companion object { const val FIELD = "equipments" }
}

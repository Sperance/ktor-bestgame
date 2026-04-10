package features.stats

import application.model.CounterEntry
import application.model.RecordEntry
import base.annotations.Immutable
import base.annotations.ReadOnly
import base.model.BaseEntity
import base.table.BaseTable
import features.characters.CharacterTable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.json.jsonb

/**
 * Таблица статистики персонажей.
 *
 * Отделена от CharacterTable, чтобы частые обновления статистики
 * (каждый удар, каждое убийство) не вызывали optimistic lock конфликтов
 * на основной таблице персонажа.
 *
 * Одна строка = один персонаж. Создаётся при создании персонажа.
 */
object CharacterStatsTable : BaseTable("character_stats") {

    /** Владелец статистики (1:1 с character) */
    val characterId = long("character_id").references(CharacterTable.id).uniqueIndex()

    /**
     * Счётчики: убийства, смерти, урон, золото и т.д.
     * JSONB: ["K1:150", "K2:3", "K3:54000"]
     */
    val counters = jsonb<MutableSet<CounterEntry>>(
        name = "counters",
        jsonConfig = Json
    )

    /**
     * Рекорды: макс. урон, макс. серия, макс. уровень и т.д.
     * JSONB: ["M1:9999.0", "M3:15.0"]
     */
    val records = jsonb<MutableSet<RecordEntry>>(
        name = "records",
        jsonConfig = Json
    )
}

@Serializable
data class CharacterStats(
    @ReadOnly
    override val id: Long? = null,

    @Immutable
    val characterId: Long,

    val counters: MutableSet<CounterEntry> = mutableSetOf(),

    val records: MutableSet<RecordEntry> = mutableSetOf(),

    @ReadOnly
    override val version: Long = 1,

    @ReadOnly
    val createdAt: String? = null,

    @ReadOnly
    val updatedAt: String? = null
) : BaseEntity

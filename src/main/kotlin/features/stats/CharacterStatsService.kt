package features.stats

import application.enums.EnumCounter
import application.enums.EnumRecord
import application.model.CounterEntry
import application.model.RecordEntry
import base.exception.NotFoundException
import base.service.BaseService
import features.characters.CharacterRepository
import kotlinx.serialization.builtins.SetSerializer
import application.model.CompactCounterEntrySerializer
import application.model.CompactRecordEntrySerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class CharacterStatsService(
    private val statsRepo: CharacterStatsRepository = CharacterStatsRepository(),
    private val charRepo: CharacterRepository = CharacterRepository()
) : BaseService<CharacterStats, CharacterStatsTable>(statsRepo, CharacterStats.serializer()) {

    // ==================== READ ====================

    /**
     * Получить статистику персонажа. Если ещё не создана — создаёт пустую.
     */
    fun getByCharacter(characterId: Long): CharacterStats {
        return statsRepo.findByCharacter(characterId) ?: createEmpty(characterId)
    }

    // ==================== COUNTERS ====================

    /**
     * Увеличить один счётчик.
     *
     * ```kotlin
     * statsService.incrementCounter(charId, EnumCounter.KILLS, 1)
     * statsService.incrementCounter(charId, EnumCounter.DAMAGE_DEALT, 1500)
     * ```
     */
    fun incrementCounter(characterId: Long, key: EnumCounter, amount: Long = 1): CharacterStats {
        return incrementCounters(characterId, mapOf(key to amount))
    }

    /**
     * Увеличить несколько счётчиков за раз (например, после боя).
     *
     * ```kotlin
     * statsService.incrementCounters(charId, mapOf(
     *     EnumCounter.KILLS to 1,
     *     EnumCounter.DAMAGE_DEALT to 1500,
     *     EnumCounter.CRITICAL_HITS to 3,
     *     EnumCounter.GOLD_EARNED to 250
     * ))
     * ```
     */
    fun incrementCounters(characterId: Long, deltas: Map<EnumCounter, Long>): CharacterStats {
        val stats = getByCharacter(characterId)
        val counters = stats.counters.toMutableSet()

        for ((key, amount) in deltas) {
            val existing = counters.find { it.key == key }
            if (existing != null) {
                existing.value += amount
            } else {
                counters.add(CounterEntry(key, amount))
            }
        }

        return saveCountersAndRecords(stats, counters, stats.records)
    }

    // ==================== RECORDS ====================

    /**
     * Обновить рекорд (только если новое значение больше текущего).
     *
     * ```kotlin
     * statsService.updateRecord(charId, EnumRecord.MAX_DAMAGE, 9999.0)
     * ```
     * @return true если рекорд обновлён, false если текущий лучше
     */
    fun updateRecord(characterId: Long, key: EnumRecord, value: Int): Boolean {
        return updateRecords(characterId, mapOf(key to value)).isNotEmpty()
    }

    /**
     * Обновить несколько рекордов за раз. Обновляются только те, где новое значение > текущего.
     *
     * ```kotlin
     * statsService.updateRecords(charId, mapOf(
     *     EnumRecord.MAX_DAMAGE to 9999,
     *     EnumRecord.MAX_KILL_STREAK to 15
     * ))
     * ```
     * @return список ключей, которые действительно обновились
     */
    fun updateRecords(characterId: Long, candidates: Map<EnumRecord, Int>): List<EnumRecord> {
        val stats = getByCharacter(characterId)
        val records = stats.records.toMutableSet()
        val updated = mutableListOf<EnumRecord>()

        for ((key, newValue) in candidates) {
            val existing = records.find { it.key == key }
            if (existing != null) {
                if (newValue > existing.value) {
                    existing.value = newValue
                    updated += key
                }
            } else {
                records.add(RecordEntry(key, newValue))
                updated += key
            }
        }

        if (updated.isNotEmpty()) {
            saveCountersAndRecords(stats, stats.counters, records)
        }

        return updated
    }

    // ==================== BATCH (после боя) ====================

    /**
     * Обновить и счётчики и рекорды одним вызовом.
     * Удобно вызывать после боя, квеста и т.д.
     *
     * ```kotlin
     * statsService.afterBattle(charId,
     *     counters = mapOf(
     *         EnumCounter.KILLS to 3,
     *         EnumCounter.DAMAGE_DEALT to 12000
     *     ),
     *     records = mapOf(
     *         EnumRecord.MAX_DAMAGE to 4500.0,
     *         EnumRecord.MAX_KILL_STREAK to 3.0
     *     )
     * )
     * ```
     */
    fun afterBattle(
        characterId: Long,
        counters: Map<EnumCounter, Long> = emptyMap(),
        records: Map<EnumRecord, Int> = emptyMap()
    ): CharacterStats {
        val stats = getByCharacter(characterId)

        // Обновляем счётчики
        val updatedCounters = stats.counters.toMutableSet()
        for ((key, amount) in counters) {
            val existing = updatedCounters.find { it.key == key }
            if (existing != null) existing.value += amount
            else updatedCounters.add(CounterEntry(key, amount))
        }

        // Обновляем рекорды
        val updatedRecords = stats.records.toMutableSet()
        for ((key, newValue) in records) {
            val existing = updatedRecords.find { it.key == key }
            if (existing != null) {
                if (newValue > existing.value) existing.value = newValue
            } else {
                updatedRecords.add(RecordEntry(key, newValue))
            }
        }

        return saveCountersAndRecords(stats, updatedCounters, updatedRecords)
    }

    // ==================== INTERNAL ====================

    private fun createEmpty(characterId: Long): CharacterStats {
        if (!charRepo.exists(characterId)) {
            throw NotFoundException("Character(id=$characterId) not found")
        }
        val json = JsonObject(mapOf(
            "characterId" to JsonPrimitive(characterId)
        ))
        return statsRepo.create(json)
    }

    /**
     * Сохранить обновлённые counters и records в БД.
     * Сериализуем наборы в JSON и вызываем update.
     */
    private fun saveCountersAndRecords(
        stats: CharacterStats,
        counters: MutableSet<CounterEntry>,
        records: MutableSet<RecordEntry>
    ): CharacterStats {
        val countersJson = Json.encodeToJsonElement(
            SetSerializer(CompactCounterEntrySerializer), counters
        )
        val recordsJson = Json.encodeToJsonElement(
            SetSerializer(CompactRecordEntrySerializer), records
        )

        val json = JsonObject(mapOf(
            "version" to JsonPrimitive(stats.version),
            "counters" to countersJson,
            "records" to recordsJson
        ))

        return statsRepo.update(stats.id, json)
    }
}

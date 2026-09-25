package config

import application.enums.EnumModifierOperation
import application.enums.EnumModifierSource
import application.enums.EnumRarity
import application.enums.IntEnumStat
import base.exception.model.EquipmentExceptions
import features.logic.modifiers.ModifierDefinition
import features.logic.modifiers.ModifierTier
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Уникальные предметы (с 0.39.0 - данными в `resources/content/uniques.json`).
 *
 * Механика как в POE: у уникалки нет случайных префиксов и суффиксов
 * ([EnumRarity.UNIQUE] роллит 0 и 0), вместо них закреплённый набор строк - `lines`, -
 * из которых сидер делает модификаторы с источником [EnumModifierSource.UNIQUE] и одним тиром
 * на уровне предмета. Значения внутри диапазонов роллятся при получении предмета, поэтому две
 * копии одной уникалки отличаются друг от друга.
 *
 * Откуда уникалка падает, решают её `pools`, а не список в коде: обычная состоит в `drop`,
 * `unique:world` и `unique:chance`, кузнечная - в `unique:smith`, уникалка босса - в
 * `boss:<код босса>`. Источник называет пулы, из которых тянет, так что уникалка босса не падает
 * ниоткуда, кроме своего босса, просто потому, что больше ни в одном пуле не состоит.
 *
 * Мифические предметы (0.53.0, `mythics.json`) устроены так же, только строк у них две-три,
 * каждая в разы сильнее лучшего тира, а падают они впятеро реже уникалок и лишь с 15-й карты.
 */
object UniqueEquipmentSeeder {

    const val FILE = "uniques.json"
    const val MYTHICS_FILE = "mythics.json"

    private val json = Json { ignoreUnknownKeys = true }

    /** Один эффект строки уникалки: диапазон `[min, max]` её единственного тира. */
    @Serializable
    data class UniqueEffect(val stat: IntEnumStat, val operation: EnumModifierOperation, val range: List<Double>)

    @Serializable
    private data class UniquesDocument(
        val uniques: List<EquipmentSeeder.EquipmentRecord> = emptyList(),
        val mythics: List<EquipmentSeeder.EquipmentRecord> = emptyList(),
    )

    /** Шаблоны уникалок и мифических предметов в том виде, в каком они лежат в файлах; редкость задаёт файл. */
    val records: List<EquipmentSeeder.EquipmentRecord> by lazy {
        read(FILE, EnumRarity.UNIQUE) { it.uniques } + read(MYTHICS_FILE, EnumRarity.MYTHICAL) { it.mythics }
    }

    private fun read(file: String, rarity: EnumRarity, list: (UniquesDocument) -> List<EquipmentSeeder.EquipmentRecord>) =
        list(json.decodeFromString(UniquesDocument.serializer(), ContentResource.read(file))).map { record ->
            if (record.lines.isEmpty() || record.lines.any { line -> line.isEmpty() || line.any { it.range.size != 2 || it.range[0] > it.range[1] } })
                throw EquipmentExceptions.funException("UniqueEquipmentSeeder", "${record.code} in $file needs lines of [min, max] ranges")
            record.copy(rarity = rarity)
        }

    fun modifierCode(itemCode: String, index: Int): String = "UNIQUE_${itemCode}_$index"

    private val modifierRecords: List<ModifierRecord> by lazy {
        records.flatMap { unique ->
            unique.lines.mapIndexed { index, line ->
                ModifierRecord(
                    code = modifierCode(unique.code, index),
                    source = EnumModifierSource.UNIQUE,
                    effects = line.map { EffectRecord(it.stat, it.operation) },
                    // У уникалки один тир: диапазон фиксирован самим предметом
                    tiers = listOf(TierRecord(unique.itemLevel, line.map { it.range })),
                    tags = listOf("unique", unique.slot.name.lowercase()),
                )
            }
        }
    }

    /**
     * Описания модификаторов уникальных предметов.
     */
    fun seedDefinitions(): List<ModifierDefinition> = modifierRecords.toDefinitions()

    /**
     * Тиры модификаторов уникальных предметов - по одному на модификатор.
     */
    fun seedTiers(definitions: List<ModifierDefinition>): List<ModifierTier> = modifierRecords.toTiers(definitions)
}

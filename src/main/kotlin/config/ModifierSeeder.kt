package config

import base.exception.model.ModifierExceptions
import features.logic.modifiers.ModifierDefinition
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Начальные данные коллекции `ModifierDefinition` - описания вместе с их тирами (с 0.56.0).
 *
 * Набор модификаторов построен по модели Path of Exile и с 0.39.0 лежит данными в
 * `resources/content/modifiers.json`, как экипировка:
 * - `source` задаёт, откуда модификатор берётся: PREFIX и SUFFIX роллятся из пулов предмета,
 *   IMPLICIT/ENCHANTMENT/CORRUPTION/UNIQUE висят на предмете всегда;
 * - `operation` задаёт, как значение применяется: ADD - плоская прибавка,
 *   INCREASED - аддитивные проценты, MORE - мультипликативные проценты, SET - замена базы;
 * - модификатор может быть составным и менять несколько статов сразу
 *   ("+# to maximum Life and Mana"), тогда у него несколько эффектов;
 * - тир 1 - лучший и требует самый высокий item level, дальше значения и требования падают;
 * - в каких пулах он состоит и с каким весом, говорит `content/pools.json`, см. [PoolSeeder].
 */
object ModifierSeeder {

    const val FILE = "modifiers.json"

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class ModifiersDocument(val modifiers: List<ModifierRecord> = emptyList())

    val records: List<ModifierRecord> by lazy {
        val records = json.decodeFromString(ModifiersDocument.serializer(), ContentResource.read(FILE)).modifiers
        records.firstNotNullOfOrNull { it.problem() }?.let { throw ModifierExceptions.funException("ModifierSeeder", it) }
        records
    }

    /**
     * Документы коллекции `ModifierDefinition`.
     */
    fun seedDefinitions(): List<ModifierDefinition> = records.toDefinitions()
}

package config

import base.exception.model.ModifierExceptions
import features.logic.modifiers.ModifierDefinition
import features.logic.modifiers.ModifierTier
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Начальные данные коллекций `ModifierDefinition` и `ModifierTier`.
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
 * - `pools` - в каких пулах он состоит и с каким весом, см. [features.logic.pools.Pooled].
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

    /**
     * Документы коллекции `ModifierTier` для уже сохранённых описаний.
     *
     * @param definitions описания, которым принадлежат тиры (нужны их _id)
     */
    fun seedTiers(definitions: List<ModifierDefinition>): List<ModifierTier> = records.toTiers(definitions)
}

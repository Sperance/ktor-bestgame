package ru.descend.features.poe.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import ru.descend.domain.modifiers.ModifierEffect
import ru.descend.domain.modifiers.ModifierOperation
import ru.descend.domain.modifiers.ModifierValue
import ru.descend.domain.modifiers.ValueExpression
import ru.descend.domain.stats.StatId
import ru.descend.features.poe.catalog.PoeCatalog
import ru.descend.features.poe.catalog.objects
import ru.descend.features.poe.catalog.string
import ru.descend.features.poe.catalog.strings

/** Explicit adapters only. Unknown PoE mechanics stay visible, never silently become generic +stats.
 * Extend this registry with tested handlers when implementing the corresponding combat subsystem. */
class PoeEffectRegistry(private val handlers: Map<String, (Int) -> ModifierEffect> = commonStats) {
    companion object {
        private fun flat(target: String): (Int) -> ModifierEffect = { index ->
            ModifierEffect.Stat(StatId(target), ModifierOperation.FLAT, ValueExpression.ModifierValue(index))
        }
        val commonStats = mapOf(
            "additional_strength" to flat("strength"),
            "additional_dexterity" to flat("dexterity"),
            "additional_intelligence" to flat("intelligence"),
            "base_maximum_life" to flat("maximum_life"),
            "base_maximum_mana" to flat("maximum_mana"),
            "base_fire_damage_resistance_%" to flat("fire_resistance"),
            "base_cold_damage_resistance_%" to flat("cold_resistance"),
            "base_lightning_damage_resistance_%" to flat("lightning_resistance"),
            "base_chaos_damage_resistance_%" to flat("chaos_resistance")
        )
    }
    fun effects(definition: JsonObject): List<ModifierEffect> = definition.objects("stats").mapIndexedNotNull { i, s -> handlers[s.string("id")]?.invoke(i) }
    fun unsupported(definition: JsonObject): List<String> = definition.objects("stats").map { it.string("id") }.filterNot { it in handlers }
    fun fullySupported(definition: JsonObject) = unsupported(definition).isEmpty() && definition.objects("grants_effects").isEmpty() && definition.strings("adds_tags").isEmpty()
}

@Serializable
@kotlinx.serialization.SerialName("features.poe.PoeCapabilities")
data class PoeCapabilities(
    val version: String = "3.29.3.3",
    val baseRecords: Int = PoeCatalog.bundled.bases.size,
    val modifierRecords: Int = PoeCatalog.bundled.mods.size,
    val profile: String = "compact-v1",
    val apiRevision: Int = 3,
    val equipmentComparison: Boolean = true,
    val catalogSearch: Boolean = true,
    val craftOptions: Boolean = true,
    val characterCommands: Boolean = true,
    val versionedCrud: Boolean = true,
    val characterStats: String = "/api/v1/character/{id}/stats",
    val statsRuleset: String = "compact-character-v1",
    val uniqueRecords: Int = PoeCatalog.bundled.bases.values.count { PoeCatalog.bundled.unique(it) },
    val currencies: List<PoeCurrency> = PoeCurrency.entries,
    val unsupported: List<String> = listOf("Vaal/Chance outcomes", "influenced and Eldritch crafting", "essence/fossil/bench/veiled crafting", "socket/gem mechanics", "full combat evaluation of imported raw stats"),
    val probabilityPolicy: String = "Compact base drops are uniform among eligible bases, including two curated uniques; ordinary rarity 50/35/15. Rare affix counts 4/5/6 use 8/3/1 weights. Affix filling stops if the compact pool is exhausted. These are server balance rules, not verified GGG probabilities."
)

package features.poe

import features.logic.modifiers.*
import features.logic.stats.StatId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

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
data class PoeCapabilities(
    val version: String = "3.29.3.3",
    val baseRecords: Int = 5461,
    val modifierRecords: Int = 40355,
    val currencies: List<PoeCurrency> = PoeCurrency.entries,
    val unsupported: List<String> = listOf("unique drop tables and unique crafting", "Vaal/Chance outcomes", "influenced and Eldritch crafting", "essence/fossil/bench/veiled crafting", "socket/gem mechanics", "full combat evaluation of imported raw stats"),
    val probabilityPolicy: String = "Base drops are uniform among eligible bases; rarity 50/35/15. Rare affix counts 4/5/6 use 8/3/1 weights. These are server balance rules, not verified GGG probabilities."
)

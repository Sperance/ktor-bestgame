package ru.descend.features.character.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import ru.descend.domain.modifiers.*
import ru.descend.domain.stats.*
import ru.descend.features.character.model.*
import ru.descend.features.equipment.model.*
import ru.descend.features.poe.catalog.*
import ru.descend.shared.http.invalid

@Serializable data class WeaponStats(val minimumPhysical: Double, val maximumPhysical: Double, val attacksPerSecond: Double, val criticalChance: Double, val accuracy: Double, val averageHit: Double, val dps: Double)
@Serializable data class CharacterStats(val version: Long, val values: Map<String, Double>, val weapons: Map<EquipmentSlot, WeaponStats>, val unsupported: List<String>)

/** One calculation path for reads and equipment validation. Raw local stats never become global. */
class CharacterStatsCalculator {
    fun calculate(character: Character, templates: Map<String, Equipment>, catalog: PoeCatalog,
        definitions: Map<ModifierRef, ModifierDefinition> = emptyMap(),
        passiveTree: ru.descend.features.passives.model.PassiveTree = ru.descend.features.passives.domain.PassiveTreeSeed.tree): CharacterStats {
        require(passiveTree.revision == character.passiveTreeRevision) { "Passive tree revision mismatch" }
        val passiveRules = ru.descend.features.passives.domain.PassiveRules(passiveTree)
        passiveRules.validate(character.passiveNodes, character.level.toInt())
        val passives = passiveRules.modifiers(character.passiveNodes)
        val raw = mutableMapOf<String, Double>(); val unsupported = sortedSetOf<String>()
        val weaponRows = mutableMapOf<EquipmentSlot, Triple<Map<String, Double>, Equipment, JsonObject?>>()
        val flat = mutableMapOf("strength" to 20.0, "dexterity" to 20.0, "intelligence" to 20.0, "armour" to 0.0, "evasion" to 0.0, "energy_shield" to 0.0)
        flat["attack_damage_multiplier"] = 1.0
        flat["attack_speed_multiplier"] = 1.0
        flat["critical_chance_multiplier"] = 1.0
        val allModifiers = character.params.toMutableList()
        character.equipped.forEach { (slot, uuid) ->
            val item = character.equipments.singleOrNull { it.uuid == uuid } ?: invalid("Equipped item is missing")
            val template = item.baseSnapshot ?: templates[item.equipmentId] ?: invalid("Equipment base is missing")
            val local = mutableMapOf<String, Double>()
            val base = item.poe?.baseId?.let { catalog.bases[it] }
            val properties = base?.get("properties") as? JsonObject
            if (item.poe != null) {
                if (base == null) unsupported += "base:${item.poe!!.baseId}"
                val state = item.poe!!
                (state.implicits + state.explicits).forEach { roll ->
                    val mod = catalog.mod(roll.id, roll.revision)
                    val stats = mod.objects("stats")
                    require(roll.values.size == stats.size) { "Invalid stored roll" }
                    stats.forEachIndexed { i, stat ->
                        val id = stat.string("id"); val value = roll.values[i].toDouble()
                        val destination = if (id.startsWith("local_")) local else raw
                        destination[id] = (destination[id] ?: 0.0) + value
                        if (id !in supported) unsupported += id
                    }
                }
            } else allModifiers += item.params
            fun property(key: String, fallback: Double) = (properties?.get(key) as? JsonObject)?.int("min")?.toDouble() ?: fallback
            val quality = (item.poe?.quality ?: 0) / 100.0
            flat["armour"] = flat.getValue("armour") + StatCalculation(base = property("armour", (template as? Armor)?.defense?.toDouble() ?: 0.0), flat = local["local_base_physical_damage_reduction_rating"] ?: 0.0, increased = (local["local_physical_damage_reduction_rating_+%"] ?: 0.0) / 100 + quality).calculate()
            flat["evasion"] = flat.getValue("evasion") + StatCalculation(base = property("evasion", 0.0), flat = local["local_base_evasion_rating"] ?: 0.0, increased = quality).calculate()
            flat["energy_shield"] = flat.getValue("energy_shield") + StatCalculation(base = property("energy_shield", 0.0), flat = local["local_energy_shield"] ?: 0.0, increased = quality).calculate()
            if (template is Weapon) weaponRows[slot] = Triple(local + ("quality" to quality), template, properties)
        }
        fun amount(id: String) = raw[id] ?: 0.0
        for ((target, source) in mapOf("strength" to "additional_strength", "dexterity" to "additional_dexterity", "intelligence" to "additional_intelligence")) flat[target] = flat.getValue(target) + amount(source) + amount("additional_all_attributes")
        val skillNames = mapOf("STOCK_STRENGTH" to "strength", "STOCK_AGILITY" to "dexterity", "STOCK_INTELLECT" to "intelligence", "STOCK_HEALTH" to "maximum_life", "STOCK_MANA" to "maximum_mana", "STOCK_ARMOR" to "armour", "STOCK_EVASION" to "evasion", "STOCK_ENERGY_SHIELD" to "energy_shield")
        character.stockSkills.forEach { skill -> skillNames[skill.stat.name]?.let { flat[it] = (flat[it] ?: 0.0) + skill.value.toDouble() } ?: run { unsupported += skill.stat.name } }
        fun resolve(base: Map<String, Double>): Map<String, Double> {
            val context = ModifierContext(stats = base.mapKeys { StatId(it.key) })
            val resolved = allModifiers.mapNotNull { modifier ->
                val d = definitions[ModifierRef(modifier.definitionId, modifier.definitionRevision)]
                if (d == null) { unsupported += "missing:${modifier.definitionId}@${modifier.definitionRevision}"; return@mapNotNull null }
                if (d.scope != ModifierScope.CHARACTER || d.effects.any { it !is ModifierEffect.Stat }) { unsupported += "context:${d.id}"; return@mapNotNull null }
                if (!d.conditions.all { DefaultConditionEvaluator().evaluate(it, context) }) return@mapNotNull null
                ResolvedModifier(d.id, modifier.tier, modifier.source, d.effects, d.priority, modifier, d.scope)
            }.sortedBy { it.priority }
            return DefaultStatResolver(resolved + passives, ValueExpressionEvaluator()).resolveAll(StatContext(context.stats, context)).mapKeys { it.key.value }
        }
        val attrs = resolve(flat)
        flat["maximum_life"] = (flat["maximum_life"] ?: 0.0) + 50 + (character.level - 1).coerceAtLeast(0) * 12 + attrs.getValue("strength") / 2 + amount("base_maximum_life")
        flat["maximum_mana"] = (flat["maximum_mana"] ?: 0.0) + 34 + (character.level - 1).coerceAtLeast(0) * 6 + attrs.getValue("intelligence") / 2 + amount("base_maximum_mana")
        flat["accuracy"] = StatCalculation(base = (flat["accuracy"] ?: 0.0) + attrs.getValue("dexterity") * 2, increased = amount("accuracy_rating_+%") / 100).calculate()
        flat["life_regeneration"] = amount("base_life_regeneration_rate_per_minute") / 60
        flat["mana_regeneration"] = flat.getValue("maximum_mana") * 0.0175 * (1 + amount("mana_regeneration_rate_+%") / 100)
        flat["movement_speed"] = (1 + amount("base_movement_velocity_+%") / 100).coerceAtLeast(0.0)
        flat["item_rarity"] = amount("base_item_found_rarity_+%")
        flat["spell_damage_increased"] = amount("spell_damage_+%") / 100
        flat["monster_flee_chance"] = amount("global_hit_causes_monster_flee_%").coerceIn(0.0, 100.0)
        for (element in listOf("fire", "cold", "lightning", "chaos")) flat[element + "_resistance"] = amount("base_${element}_damage_resistance_%") + if (element == "chaos") 0.0 else amount("base_resist_all_elements_%")
        val result = resolve(flat).toMutableMap()
        for (element in listOf("fire", "cold", "lightning", "chaos")) {
            val key = element + "_resistance"; result[key + "_uncapped"] = result[key] ?: 0.0
            result[key] = result.getValue(key + "_uncapped").coerceIn(-200.0, 75.0)
        }
        result["unarmed_dps"] = (6 + result.getValue("strength") * .2) * result.getValue("attack_damage_multiplier") * result.getValue("attack_speed_multiplier")
        val weapons = weaponRows.mapValues { (_, row) ->
            val (local, template, props) = row; val weapon = template as Weapon
            fun damage(key: String, fallback: Double) = props?.get(key)?.jsonPrimitive?.doubleOrNull ?: fallback
            val inc = (local["local_physical_damage_+%"] ?: 0.0) / 100 + (local["quality"] ?: 0.0)
            val min = StatCalculation(base = damage("physical_damage_min", weapon.damage_min), flat = local["local_minimum_added_physical_damage"] ?: 0.0, increased = inc).calculate() + amount("attack_minimum_added_physical_damage")
            val max = StatCalculation(base = damage("physical_damage_max", weapon.damage_max), flat = local["local_maximum_added_physical_damage"] ?: 0.0, increased = inc).calculate() + amount("attack_maximum_added_physical_damage")
            val speed = (1000 / damage("attack_time", 1000 / weapon.attackSpeed.coerceAtLeast(0.01))) * (1 + (local["local_attack_speed_+%"] ?: 0.0) / 100) * result.getValue("attack_speed_multiplier")
            var elemental = 0.0
            for (element in listOf("fire", "cold", "lightning", "chaos")) {
                val added = (amount("attack_minimum_added_${element}_damage") + amount("attack_maximum_added_${element}_damage")) / 2
                elemental += added * if (element == "chaos") 1.0 else (1 + amount("elemental_damage_with_attack_skills_+%") / 100)
            }
            elemental += (amount("spell_and_attack_minimum_added_lightning_damage") + amount("spell_and_attack_maximum_added_lightning_damage")) / 2 * (1 + amount("elemental_damage_with_attack_skills_+%") / 100)
            val multiplier = (1 + amount("damage_+%") / 100) * result.getValue("attack_damage_multiplier")
            val avg = ((min + max) / 2 + elemental) * multiplier
            WeaponStats(min * multiplier, max * multiplier, speed, (damage("critical_strike_chance", 500.0) / 100 * (1 + amount("critical_strike_chance_+%") / 100) * result.getValue("critical_chance_multiplier")).coerceIn(0.0, 100.0), result.getValue("accuracy") + (local["local_accuracy_rating"] ?: 0.0) * (1 + amount("accuracy_rating_+%") / 100), avg, avg * speed)
        }
        require(result.values.all { it.isFinite() } && weapons.values.all { it.dps.isFinite() }) { "Non-finite character stats" }
        return CharacterStats(character.version, result.toSortedMap(), weapons, unsupported.toList())
    }
    companion object {
        val supported: Set<String> = setOf("accuracy_rating_+%", "additional_all_attributes", "additional_dexterity", "additional_intelligence", "additional_strength", "attack_maximum_added_chaos_damage", "attack_maximum_added_cold_damage", "attack_maximum_added_fire_damage", "attack_maximum_added_lightning_damage", "attack_maximum_added_physical_damage", "attack_minimum_added_chaos_damage", "attack_minimum_added_cold_damage", "attack_minimum_added_fire_damage", "attack_minimum_added_lightning_damage", "attack_minimum_added_physical_damage", "base_chaos_damage_resistance_%", "base_cold_damage_resistance_%", "base_fire_damage_resistance_%", "base_item_found_rarity_+%", "base_life_regeneration_rate_per_minute", "base_lightning_damage_resistance_%", "base_maximum_life", "base_maximum_mana", "base_movement_velocity_+%", "base_resist_all_elements_%", "critical_strike_chance_+%", "damage_+%", "elemental_damage_with_attack_skills_+%", "global_hit_causes_monster_flee_%", "local_accuracy_rating", "local_attack_speed_+%", "local_base_evasion_rating", "local_base_physical_damage_reduction_rating", "local_energy_shield", "local_maximum_added_physical_damage", "local_minimum_added_physical_damage", "local_physical_damage_+%", "local_physical_damage_reduction_rating_+%", "mana_regeneration_rate_+%", "spell_and_attack_maximum_added_lightning_damage", "spell_and_attack_minimum_added_lightning_damage", "spell_damage_+%")
    }
}

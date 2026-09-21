import application.enums.EnumEquipmentType
import application.enums.EnumModifierSource
import application.enums.EnumRarity
import config.EquipmentSeeder
import config.ModifierSeeder
import config.UniqueEquipmentSeeder
import features.data.equipment.equipment_data.Equipment
import features.logic.modifiers.ModifierDefinition
import features.logic.modifiers.ModifierTier
import org.junit.Test

/**
 * Проверки справочных данных сидеров. Mongo не нужна: сидеры - чистые данные.
 */
class SeedDataTest {

    private val definitions: List<ModifierDefinition> =
        ModifierSeeder.seedDefinitions() + UniqueEquipmentSeeder.seedDefinitions()

    private val tiers: List<ModifierTier> =
        ModifierSeeder.seedTiers(definitions) + UniqueEquipmentSeeder.seedTiers(definitions)

    private val equipment: List<Equipment> = EquipmentSeeder(definitions).seed()

    @Test
    fun modifier_codes_are_unique() {
        val duplicates = definitions.groupBy { it.code }.filterValues { it.size > 1 }.keys
        assert(duplicates.isEmpty()) { "Duplicate modifier codes: $duplicates" }
    }

    @Test
    fun modifier_ids_are_unique_and_stable_between_seeds() {
        assert(definitions.map { it._id }.toSet().size == definitions.size) { "Duplicate modifier definition ids" }
        assert(tiers.map { it._id }.toSet().size == tiers.size) { "Duplicate modifier tier ids" }

        val again = ModifierSeeder.seedDefinitions() + UniqueEquipmentSeeder.seedDefinitions()
        assert(again.associate { it.code to it._id } == definitions.associate { it.code to it._id }) {
            "Modifier definition ids changed between seeds"
        }
    }

    @Test
    fun every_definition_has_effects_and_tiers() {
        val tiersByModifier = tiers.groupBy { it.modifierId }

        definitions.forEach { definition ->
            assert(definition.effects.isNotEmpty()) { "${definition.code} has no effects" }

            val own = tiersByModifier[definition._id].orEmpty()
            assert(own.isNotEmpty()) { "${definition.code} has no tiers" }
            assert(own.map { it.tier }.toSet() == (1..own.size).toSet()) {
                "${definition.code} has broken tier numbering: ${own.map { it.tier }}"
            }
        }
    }

    @Test
    fun every_tier_has_a_value_per_effect() {
        val definitionsById = definitions.associateBy { it._id }

        tiers.forEach { tier ->
            val definition = definitionsById.getValue(tier.modifierId)
            assert(tier.values.size == definition.effects.size) {
                "${definition.code} T${tier.tier}: ${definition.effects.size} effects, ${tier.values.size} values"
            }
            tier.values.forEach { value ->
                assert(value.valueMin <= value.valueMax) {
                    "${definition.code} T${tier.tier}: broken range ${value.valueMin}..${value.valueMax}"
                }
            }
            assert(tier.minItemLevel >= 1) { "${definition.code} T${tier.tier}: minItemLevel ${tier.minItemLevel}" }
            assert(tier.weight > 0) { "${definition.code} T${tier.tier}: weight ${tier.weight}" }
        }
    }

    @Test
    fun better_tiers_are_stronger_and_require_higher_item_level() {
        tiers.groupBy { it.modifierId }.forEach { (_, own) ->
            val sorted = own.sortedBy { it.tier }
            sorted.zipWithNext { better, worse ->
                assert(better.minItemLevel >= worse.minItemLevel) {
                    "tier ${better.tier} requires less item level than tier ${worse.tier}"
                }
                better.values.forEachIndexed { index, value ->
                    assert(value.valueMax >= worse.values[index].valueMax) {
                        "tier ${better.tier} is weaker than tier ${worse.tier}"
                    }
                }
            }
        }
    }

    @Test
    fun composite_modifiers_roll_all_their_effects_together() {
        val definitionsById = definitions.associateBy { it._id }
        val composite = tiers.filter { definitionsById.getValue(it.modifierId).isComposite() }

        assert(composite.isNotEmpty()) { "No composite modifiers in seed data" }

        composite.forEach { tier ->
            val rolled = tier.roll()
            assert(rolled.size == tier.values.size) { "rolled ${rolled.size} values for ${tier.values.size} effects" }

            rolled.forEachIndexed { index, value ->
                val range = tier.values[index]
                // to1Digits округляет, поэтому допускаем шаг округления на границах
                assert(value >= range.valueMin - 0.05 && value <= range.valueMax + 0.05) {
                    "${definitionsById.getValue(tier.modifierId).code}: $value out of ${range.valueMin}..${range.valueMax}"
                }
            }
        }
    }

    @Test
    fun equipment_codes_are_unique() {
        val duplicates = equipment.groupBy { it.code }.filterValues { it.size > 1 }.keys
        assert(duplicates.isEmpty()) { "Duplicate equipment codes: $duplicates" }
    }

    @Test
    fun equipment_ids_are_stable_between_seeds() {
        val second = EquipmentSeeder(definitions).seed()
        val first = equipment.associate { it.code to it._id }

        second.forEach { item ->
            assert(first[item.code] == item._id) { "${item.code} got a different _id between seeds" }
        }
    }

    @Test
    fun every_slot_has_three_uniques() {
        val bySlot = equipment.filter { it.rarity == EnumRarity.UNIQUE }.groupBy { it.slot }

        EnumEquipmentType.entries.forEach { slot ->
            val items = bySlot[slot].orEmpty()
            assert(items.size == 3) { "$slot has ${items.size} uniques: ${items.map { it.code }}" }
        }
    }

    @Test
    fun uniques_carry_only_their_own_fixed_modifiers() {
        val definitionsById = definitions.associateBy { it._id }

        equipment.filter { it.rarity == EnumRarity.UNIQUE }.forEach { item ->
            assert(item.modifierIds.isNotEmpty()) { "${item.code} has no modifiers" }

            item.modifierIds.forEach { id ->
                val definition = definitionsById[id]
                assert(definition != null) { "${item.code} references unknown modifier $id" }
                assert(definition!!.source == EnumModifierSource.UNIQUE) {
                    "${item.code} carries a rollable modifier ${definition.code}"
                }
            }
        }
    }

    @Test
    fun rollable_items_have_both_prefixes_and_suffixes_available() {
        val definitionsById = definitions.associateBy { it._id }

        equipment.filter { it.rarity != EnumRarity.UNIQUE }.forEach { item ->
            val sources = item.modifierIds.mapNotNull { definitionsById[it]?.source }
            assert(sources.contains(EnumModifierSource.PREFIX)) { "${item.code} has no prefixes to roll" }
            assert(sources.contains(EnumModifierSource.SUFFIX)) { "${item.code} has no suffixes to roll" }
        }
    }
}

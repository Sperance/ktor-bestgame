import application.enums.EnumEquipmentType
import application.enums.EnumModifierSource
import application.enums.EnumRarity
import config.EquipmentSeeder
import config.ModifierSeeder
import config.RedemptionSeeder
import config.UniqueEquipmentSeeder
import features.data.equipment.equipment_data.Equipment
import features.data.redemptionCodes.RedemptionKind
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
        // Уникалки боссов кампании (0.32.0) - сверх трёх: они падают только со своего босса.
        val bySlot = equipment.filter { it.rarity == EnumRarity.UNIQUE && it.code !in config.UniqueEquipmentSeeder.bossOnly }.groupBy { it.slot }

        // Самоцвет носится не на теле, а в гнезде дерева, и уникальных самоцветов
        // пока нет: их сила должна считаться вместе с деревом, а не отдельно от него.
        wearableSlots.forEach { slot ->
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

    /**
     * Сид промокодов проходит те же проверки, что и создание промокода руками.
     *
     * Сидер вставляет коды через тот же insertMany, а тот зовёт validateBeforeInsert:
     * пустой подарок или неположительное количество не отвергаются, а роняют сидинг,
     * то есть старт сервера. Именно это и случилось в 0.19.0, когда награды стали
     * обязательными, а пример с пустым списком остался.
     */
    @Test
    fun seeded_redemption_codes_pass_the_rules_that_guard_insertion() {
        val codes = RedemptionSeeder.seed()
        assert(codes.isNotEmpty()) { "No redemption codes seeded" }

        val duplicates = codes.groupBy { it.code }.filterValues { it.size > 1 }.keys
        assert(duplicates.isEmpty()) { "Duplicate redemption codes: $duplicates" }

        codes.forEach { entry ->
            assert(entry.code.isNotBlank()) { "A seeded redemption code has no code" }
            assert(entry.treasure.isNotEmpty()) { "${entry.code} gives nothing" }
            entry.treasure.forEach { reward ->
                assert(reward.amount > 0) { "${entry.code} has a reward of ${reward.amount}" }
                val needsDocument = reward.kind == RedemptionKind.ITEM || reward.kind == RedemptionKind.EQUIPMENT
                // Идентификаторы предметов и шаблонов сид знать не может, поэтому
                // на них он не ссылается - только опыт и золото.
                assert(!needsDocument) { "${entry.code} names a ${reward.kind} document the seed cannot know" }
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

    /**
     * Слоты, в которые надевают шаблоны: самоцвет живёт в гнезде, а второе кольцо -
     * это место для кольца, а не вид предмета.
     */
    private val wearableSlots = EnumEquipmentType.entries - EnumEquipmentType.JEWEL - EnumEquipmentType.RING_2

    @Test
    fun every_slot_has_ordinary_bases_of_every_rarity() {
        val ordinary = equipment.filter { it.rarity != EnumRarity.UNIQUE }.groupBy { it.slot }
        wearableSlots.forEach { slot ->
            val rarities = ordinary[slot].orEmpty().map { it.rarity }.toSet()
            // Администратор выдаёт случайный шаблон выбранной редкости и слота - ни одна пара не пустует
            val missing = listOf(EnumRarity.COMMON, EnumRarity.UNCOMMON, EnumRarity.RARE, EnumRarity.EPIC, EnumRarity.MYTHICAL) - rarities
            assert(missing.isEmpty()) { "$slot has no ordinary base of $missing" }
        }
        assert(equipment.none { it.slot == EnumEquipmentType.RING_2 }) { "RING_2 is a place, not a kind of item" }
    }

    @Test
    fun armour_rolls_only_the_local_defences_its_base_carries() {
        val byId = definitions.associateBy { it._id }
        equipment.filter { it.rarity != EnumRarity.UNIQUE }.forEach { item ->
            val baseStats = item.baseParams.flatMap { byId.getValue(it.modifierId).stats() }.toSet()
            val foreign = item.modifierIds.mapNotNull { byId[it] }
                .filter { it.isLocal && it.isNaturalAffix() && !it.tags.orEmpty().contains("weapon") }
                .filterNot { baseStats.containsAll(it.stats()) }
            assert(foreign.isEmpty()) { "${item.code} rolls local modifiers its base does not carry: ${foreign.map { it.code }}" }
        }
    }

    @Test
    fun every_ordinary_base_has_an_icon() {
        val icons = kotlinx.serialization.json.Json.parseToJsonElement(
            javaClass.classLoader.getResource("icons/icons.json")!!.readText()
        ).let { (it as kotlinx.serialization.json.JsonObject)["icons"] as kotlinx.serialization.json.JsonObject }
        val missing = equipment.filter { it.slot != EnumEquipmentType.JEWEL }.map { "equipment.${it.code}" }.filterNot { it in icons }
        assert(missing.isEmpty()) { "Bases without an icon: $missing" }
    }
}

import application.enums.EnumCurrencyOrb
import application.enums.EnumEquipmentType
import application.enums.EnumInfluence
import application.enums.EnumModifierOperation
import application.enums.EnumModifierSource
import application.enums.EnumRarity
import application.enums.EnumStatStock
import config.EquipmentSeeder
import config.ModifierSeeder
import config.UniqueEquipmentSeeder
import features.data.equipment.equipment_data.Armor
import features.data.inventory.CharacterEquipment
import features.logic.bench.CraftingBench
import features.logic.currency.CurrencyApplier
import features.logic.modifiers.Modifier
import features.logic.modifiers.ModifierDefinition
import features.logic.modifiers.ModifierEffect
import features.logic.modifiers.ModifierRoller
import features.logic.pools.Pools
import org.junit.Test

/**
 * Группы, веса, верстак, закрепление и влияние - то, что проверяется без базы:
 * выбор аффиксов над описаниями, справочные данные и правила, которые отсекают
 * предмет до обращения к кэшам.
 */
class ModifierRollTest {

    private companion object {
        const val TEST_POOL = "test"
    }

    private val definitions: List<ModifierDefinition> =
        ModifierSeeder.seedDefinitions() + UniqueEquipmentSeeder.seedDefinitions()

    private fun definition(code: String, source: EnumModifierSource, group: String? = null, weight: Int = 1000) =
        ModifierDefinition(
            code = code,
            effects = listOf(ModifierEffect(EnumStatStock.STOCK_HEALTH, EnumModifierOperation.ADD)),
            source = source,
            group = group,
            pools = mapOf(TEST_POOL to weight)
        )

    private fun weighted(definitions: List<ModifierDefinition>) = Pools.of(definitions, listOf(TEST_POOL))

    private fun template(slot: EnumEquipmentType = EnumEquipmentType.HELMET) =
        Armor(slot = slot, code = "TEST_HELM", rarity = EnumRarity.RARE, itemLevel = 80)

    private fun item(rarity: EnumRarity, params: MutableList<Modifier> = mutableListOf(), influence: EnumInfluence? = null) =
        CharacterEquipment(characterId = "character", equipmentId = "equipment", rarity = rarity, params = params, influence = influence)

    // ==================== Выбор аффиксов ====================

    @Test
    fun one_group_never_lands_twice() {
        val pool = listOf(
            definition("LIFE_A", EnumModifierSource.PREFIX, group = "LIFE"),
            definition("LIFE_B", EnumModifierSource.PREFIX, group = "LIFE"),
            definition("MANA", EnumModifierSource.PREFIX),
            definition("FIRE", EnumModifierSource.SUFFIX),
        )
        repeat(200) {
            val picked = ModifierRoller.pickAffixes(weighted(pool), prefixes = 3, suffixes = 3)
            assert(picked.map { it.family() }.toSet().size == picked.size) { "Two modifiers of one group: ${picked.map { it.code }}" }
            assert(picked.size == 3) { "Free slots left while the pool still had other groups: ${picked.map { it.code }}" }
        }
    }

    @Test
    fun a_group_already_on_the_item_is_skipped() {
        val pool = listOf(
            definition("LIFE_A", EnumModifierSource.PREFIX, group = "LIFE"),
            definition("MANA", EnumModifierSource.PREFIX),
        )
        repeat(50) {
            val picked = ModifierRoller.pickAffixes(weighted(pool), prefixes = 2, suffixes = 0, taken = listOf("LIFE"))
            assert(picked.map { it.code } == listOf("MANA")) { "A taken group was rolled again: ${picked.map { it.code }}" }
        }
    }

    @Test
    fun slots_of_each_kind_are_respected() {
        val pool = (1..6).map { definition("P$it", EnumModifierSource.PREFIX) } + (1..6).map { definition("S$it", EnumModifierSource.SUFFIX) }
        repeat(50) {
            val picked = ModifierRoller.pickAffixes(weighted(pool), prefixes = 1, suffixes = 2)
            assert(picked.count { it.source == EnumModifierSource.PREFIX } == 1) { "Wrong prefix count" }
            assert(picked.count { it.source == EnumModifierSource.SUFFIX } == 2) { "Wrong suffix count" }
        }
    }

    @Test
    fun heavier_modifiers_roll_more_often() {
        val pool = listOf(
            definition("COMMON", EnumModifierSource.PREFIX, weight = 900),
            definition("RARE", EnumModifierSource.PREFIX, weight = 100),
        )
        val rolls = (1..2000).map { ModifierRoller.pickAffixes(weighted(pool), prefixes = 1, suffixes = 0).single().code }
        val rare = rolls.count { it == "RARE" }
        assert(rare in 100..350) { "A tenth of the weight rolled $rare times out of 2000" }
    }

    // ==================== Данные ====================

    @Test
    fun every_natural_affix_rolls_somewhere_and_no_weight_is_negative() {
        val broken = definitions.filter { it.pools.values.any { weight -> weight < 0 } }
        assert(broken.isEmpty()) { "Negative pool weights: ${broken.map { it.code }}" }
        val loose = definitions.filter { it.isNaturalAffix() && !it.isRetired() && it.pools.values.none { weight -> weight > 0 } }
        assert(loose.isEmpty()) { "Affixes that can never roll: ${loose.map { it.code }}" }
    }

    @Test
    fun crafted_and_influenced_modifiers_stay_out_of_template_pools() {
        EquipmentSeeder(definitions).seed().forEach { item ->
            val leaked = Pools.of(definitions, item.modifierPools).map { it.value }.filter { it.crafted || it.influence != null }
            assert(leaked.isEmpty()) { "${item.code} rolls bench or influence modifiers: ${leaked.map { it.code }}" }
        }
    }

    @Test
    fun every_influence_opens_its_own_pool() {
        EnumInfluence.entries.forEach { influence ->
            val pool = Pools.of(definitions, listOf(Pools.influence(influence))).map { it.value }
            assert(pool.isNotEmpty() && pool.all { it.influence == influence }) { "$influence pool: ${pool.map { it.code }}" }
        }
    }

    @Test
    fun every_crafted_modifier_shares_a_group_with_a_natural_one() {
        val natural = definitions.filter { it.isNaturalAffix() }.map { it.family() }.toSet()
        definitions.filter { it.crafted }.forEach {
            assert(it.isAffix()) { "${it.code} is crafted but takes no affix slot" }
            assert(it.family() in natural) { "${it.code} would stand beside its natural twin" }
        }
    }

    @Test
    fun every_influence_has_prefixes_and_suffixes() {
        EnumInfluence.entries.forEach { influence ->
            val own = definitions.filter { it.influence == influence }
            assert(own.any { it.source == EnumModifierSource.PREFIX }) { "$influence has no prefixes" }
            assert(own.any { it.source == EnumModifierSource.SUFFIX }) { "$influence has no suffixes" }
            assert(own.none { it.crafted }) { "$influence has crafted modifiers" }
        }
    }

    @Test
    fun bench_recipes_resolve_and_cover_every_crafted_modifier() {
        val recipes = CraftingBench.build(ModifierSeeder.seedDefinitions())
        assert(recipes.map { it.code }.toSet().size == recipes.size) { "Duplicate recipe codes" }
        assert(recipes.all { it.amount > 0 }) { "A recipe costs nothing" }
        assert(recipes.none { it.orb == EnumCurrencyOrb.ORB_OF_REGRET }) { "The bench is not paid with Orbs of Regret" }
        assert(recipes.all { it.values.isNotEmpty() }) { "A recipe with no value range" }

        val covered = recipes.map { it.modifierCode }.toSet()
        val missing = definitions.filter { it.crafted }.map { it.code }.filterNot { it in covered }
        assert(missing.isEmpty()) { "Crafted modifiers no recipe makes: $missing" }
    }

    // ==================== Правила без базы ====================

    private fun refused(block: () -> Unit): Boolean = runCatching(block).isFailure

    @Test
    fun fracturing_needs_a_rare_item_without_a_fractured_affix() {
        listOf(EnumRarity.COMMON, EnumRarity.UNCOMMON, EnumRarity.UNIQUE).forEach { rarity ->
            assert(refused { CurrencyApplier.apply(EnumCurrencyOrb.FRACTURING_ORB, item(rarity), template()) }) { "Fractured a $rarity item" }
        }
        val fractured = item(EnumRarity.RARE, mutableListOf(Modifier("m", listOf(1.0), "t", 1, fractured = true)))
        assert(refused { CurrencyApplier.apply(EnumCurrencyOrb.FRACTURING_ORB, fractured, template()) }) { "Fractured twice" }
    }

    @Test
    fun influence_needs_a_rare_uninfluenced_item_that_is_not_a_jewel() {
        EnumInfluence.entries.forEach { influence ->
            val orb = EnumCurrencyOrb.entries.single { EnumInfluence.byOrb(it) == influence }
            assert(refused { CurrencyApplier.apply(orb, item(EnumRarity.UNCOMMON), template()) }) { "$orb on a magic item" }
            assert(refused { CurrencyApplier.apply(orb, item(EnumRarity.RARE), template(EnumEquipmentType.JEWEL)) }) { "$orb on a jewel" }
            assert(refused { CurrencyApplier.apply(orb, item(EnumRarity.RARE, influence = EnumInfluence.SHAPER), template()) }) {
                "$orb on an influenced item"
            }
        }
    }

    @Test
    fun the_bench_refuses_what_cannot_take_a_crafted_modifier() {
        val recipe = CraftingBench.recipes.first { it.slots.isEmpty() }
        val known = listOf(recipe.code)
        listOf(EnumRarity.COMMON, EnumRarity.UNIQUE).forEach { rarity ->
            assert(refused { CraftingBench.craft(item(rarity), template(), recipe, known) }) { "Crafted on a $rarity item" }
        }
        val corrupted = item(EnumRarity.RARE).apply { this.corrupted = true }
        assert(refused { CraftingBench.craft(corrupted, template(), recipe, known) }) { "Crafted on a corrupted item" }

        val helmetOnly = CraftingBench.recipes.first { it.slots.isNotEmpty() && EnumEquipmentType.JEWEL !in it.slots }
        assert(refused { CraftingBench.craft(item(EnumRarity.RARE), template(EnumEquipmentType.JEWEL), helmetOnly, listOf(helmetOnly.code)) }) {
            "${helmetOnly.code} was crafted on a jewel"
        }
    }

    @Test
    fun the_bench_refuses_a_recipe_the_character_has_not_found() {
        val recipe = CraftingBench.recipes.first { it.slots.isEmpty() }
        assert(refused { CraftingBench.craft(item(EnumRarity.RARE), template(), recipe, emptyList()) }) { "Crafted an unknown recipe" }
    }

    @Test
    fun a_lower_map_level_rolls_a_higher_recipe_tier() {
        assert(CraftingBench.tierFor(1) > CraftingBench.tierFor(CraftingBench.MAX_MAP_LEVEL))
        assert(CraftingBench.tierFor(1) == CraftingBench.MAX_TIER)
        assert(CraftingBench.tierFor(CraftingBench.MAX_MAP_LEVEL) == 1)
    }
}

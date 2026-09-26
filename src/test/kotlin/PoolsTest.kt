import application.enums.EnumCurrencyOrb
import application.enums.EnumEquipmentType
import application.enums.EnumInfluence
import application.enums.EnumRarity
import config.CurrencySeeder
import config.EquipmentSeeder
import config.ModifierSeeder
import config.PoolSeeder
import config.UniqueEquipmentSeeder
import features.logic.campaign.CampaignContent
import features.logic.crafts.CraftsContent
import features.logic.pools.EnumPoolKind
import features.logic.pools.EnumPoolTarget
import features.logic.pools.Pool
import features.logic.pools.PoolTable
import features.logic.pools.Pooled
import features.logic.pools.Pools
import features.logic.trade.MerchantRules
import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Пулы (с 0.56.0 - одна коллекция, сид `pools.json`) и источники, которые их называют. Этот тест
 * держит их вместе: каждый пул, который называет источник, не пуст, каждый пул кем-то назван,
 * каждый код в пуле - существующая запись своего вида, а уникалка босса не состоит ни в одном
 * чужом пуле. Mongo не нужна - только файлы содержимого.
 */
class PoolsTest {

    private data class Entry(override val code: String) : Pooled

    private val definitions = ModifierSeeder.seedDefinitions() + UniqueEquipmentSeeder.seedDefinitions()
    private val equipment = EquipmentSeeder(definitions).seed()
    private val campaign = CampaignContent.file
    private val crafting = CraftsContent.file.crafting

    private val modifierPools = PoolSeeder.table(EnumPoolTarget.MODIFIER)
    private val equipmentPools = PoolSeeder.table(EnumPoolTarget.EQUIPMENT)
    private val monsterPools = PoolSeeder.table(EnumPoolTarget.MONSTER)

    /** Пулы модификаторов предметов, которые называют источники. */
    private val modifierSources: Map<String, List<String>> =
        equipment.associate { "equipment ${it.code}" to it.modifierPools }.filterValues { it.isNotEmpty() } +
            EnumInfluence.entries.flatMap { influence -> EnumEquipmentType.entries.map { slot -> "influence $influence $slot" to Pools.influence(influence, slot) } } +
            CurrencySeeder.records.values.filter { it.modifierPools.isNotEmpty() }.associate { "orb ${it.orb}" to it.modifierPools } +
            // Порча (0.66.0): у носимого, самоцвета и карты; инструмент Vaal Orb портит без имплисита.
            EnumEquipmentType.entries.filter { !it.isTool && it != EnumEquipmentType.RING_2 }.associate { "corruption $it" to listOf(Pools.corruption(it)) } +
            mapOf("smith" to crafting.modifierPools + listOf("handcrafted:smith:weapon", "handcrafted:smith:armour"), "cartographer" to crafting.mapModifierPools)

    /** Пулы экипировки и уникалок, которые называют источники. */
    private val equipmentSources: Map<String, List<String>> =
        campaign.lootTables.flatMap { (name, table) -> table.drops.filter { it.equipmentPools.isNotEmpty() }.map { "loot $name" to it.equipmentPools } }.toMap() +
            // Свои уникалки с 0.67.0 есть не у каждого босса: без них он роняет мировые.
            campaign.monsters.filter { it.boss && it.uniquePools.isNotEmpty() }.associate { "boss ${it.code}" to it.uniquePools } +
            CurrencySeeder.records.values.filter { it.uniquePools.isNotEmpty() }.associate { "orb ${it.orb}" to it.uniquePools } +
            mapOf("bosses" to campaign.bosses.uniquePools, "corruption" to campaign.corruption.uniquePools,
                "smith bases" to crafting.equipmentPools, "smith uniques" to crafting.uniquePools, "merchant" to MerchantRules.POOLS)

    private val monsterSources: Map<String, List<String>> =
        campaign.zones.associate { "map ${it.code}" to it.modifierPools } + mapOf("bosses" to campaign.bosses.modifierPools)

    /** Модификаторы монстров (0.66.0) - описания источника MONSTER. */
    private val monsterModifiers = definitions.filter { it.isMonster() }

    @Test
    fun the_first_pool_a_source_names_decides_the_weight_and_zero_excludes() {
        val table = PoolTable(listOf(
            Pool("x", EnumPoolKind.MODIFIER, mapOf("A" to 10, "B" to 0)),
            Pool("y", EnumPoolKind.MODIFIER, mapOf("A" to 90, "B" to 50, "C" to 5)),
        ))
        val entries = listOf(Entry("A"), Entry("B"), Entry("C"))
        assertEquals(listOf("A" to 10, "C" to 5), table.of(entries, listOf("x", "y")).map { it.value.code to it.weight })
        assertEquals(listOf("A" to 90, "B" to 50, "C" to 5), table.of(entries, listOf("y", "x")).map { it.value.code to it.weight })
        assertTrue(table.of(entries, listOf("z")).isEmpty())
        val random = Random(3)
        val drawn = List(2000) { Pools.draw(table.of(entries, listOf("y")), random)!!.code }
        assertTrue(drawn.count { it == "C" } < drawn.count { it == "B" }, "a lighter entry is drawn less often")
    }

    @Test
    fun pools_of_one_tag_but_different_kinds_merge_into_one_draw() {
        val table = PoolTable(listOf(Pool("drop", EnumPoolKind.EQUIPMENT, mapOf("BASE" to 100)), Pool("drop", EnumPoolKind.UNIQUE, mapOf("UNIQUE" to 5))))
        assertEquals(listOf("BASE" to 100, "UNIQUE" to 5), table.of(listOf(Entry("BASE"), Entry("UNIQUE")), listOf("drop")).map { it.value.code to it.weight })
    }

    @Test
    fun every_pool_a_source_names_has_members() {
        modifierSources.forEach { (source, tags) -> assertTrue(modifierPools.of(definitions, tags).isNotEmpty(), "$source names empty pools $tags") }
        equipmentSources.forEach { (source, tags) -> assertTrue(equipmentPools.of(equipment, tags).isNotEmpty(), "$source names empty pools $tags") }
        monsterSources.forEach { (source, tags) -> assertTrue(monsterPools.of(monsterModifiers, tags).isNotEmpty(), "$source names empty pools $tags") }
    }

    @Test
    fun every_pool_is_named_by_a_source() {
        // Общие пулы (0.66.0) никто не называет: их включают пулы слотов, см. PoolSeeder.
        val included = setOf("armour", "jewellery", "weapon", "tool")
        fun orphans(table: PoolTable, sources: Map<String, List<String>>) = table.tags - sources.values.flatten().toSet() - included
        assertEquals(emptySet(), orphans(modifierPools, modifierSources))
        assertEquals(emptySet(), orphans(equipmentPools, equipmentSources))
        assertEquals(emptySet(), orphans(monsterPools, monsterSources))
    }

    @Test
    fun every_entry_is_a_record_of_its_kind() {
        val codes: Map<EnumPoolKind, Set<String>> = mapOf(
            EnumPoolKind.MODIFIER to definitions.map { it.code }.toSet(),
            EnumPoolKind.MONSTER to monsterModifiers.map { it.code }.toSet(),
            EnumPoolKind.EQUIPMENT to equipment.filter { !it.rarity.fixed }.map { it.code }.toSet(),
            EnumPoolKind.UNIQUE to equipment.filter { it.rarity == EnumRarity.UNIQUE }.map { it.code }.toSet(),
            EnumPoolKind.MYTHIC to equipment.filter { it.rarity == EnumRarity.MYTHICAL }.map { it.code }.toSet(),
        )
        val strangers = PoolSeeder.pools.flatMap { pool -> pool.entries.keys.filterNot { it in codes.getValue(pool.kind) }.map { "${pool.kind} ${pool.code}: $it" } }
        assertEquals(emptyList(), strangers)
        val duplicates = PoolSeeder.pools.groupBy { it.kind to it.code }.filterValues { it.size > 1 }.keys
        assertEquals(emptySet(), duplicates)
    }

    @Test
    fun a_boss_unique_drops_from_its_boss_alone() {
        val bossPools = campaign.monsters.filter { it.boss }.flatMap { it.uniquePools }.toSet()
        val own = bossPools.flatMap { equipmentPools.members(it).keys }.toSet()
        assertTrue(own.isNotEmpty())
        own.forEach { code ->
            val tags = equipmentPools.tags.filter { code in equipmentPools.members(it) }
            assertEquals(1, tags.size, "$code also sits in $tags")
        }
    }

    @Test
    fun every_unique_sits_in_some_pool_and_ordinary_bases_never_in_a_unique_one() {
        equipment.filter { it.rarity == EnumRarity.UNIQUE }.forEach { unique ->
            assertTrue(equipmentPools.tags.any { (equipmentPools.members(it)[unique.code] ?: 0) > 0 }, "${unique.code} drops from nowhere")
        }
        val uniqueTags = equipmentPools.tags.filter { it.startsWith("unique:") || it.startsWith("boss:") }
        equipment.filter { it.rarity != EnumRarity.UNIQUE }.forEach { base ->
            assertTrue(uniqueTags.none { base.code in equipmentPools.members(it) }, "${base.code} sits in a unique pool")
        }
        assertTrue(CurrencySeeder.records.getValue(EnumCurrencyOrb.ORB_OF_CHANCE).uniquePools.isNotEmpty())
    }
}

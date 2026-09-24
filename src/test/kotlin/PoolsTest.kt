import application.enums.EnumCurrencyOrb
import application.enums.EnumInfluence
import application.enums.EnumRarity
import config.CurrencySeeder
import config.EquipmentSeeder
import config.ModifierSeeder
import config.UniqueEquipmentSeeder
import features.logic.campaign.CampaignContent
import features.logic.crafts.CraftsContent
import features.logic.pools.Pooled
import features.logic.pools.Pools
import features.logic.trade.MerchantRules
import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Пулы (с 0.39.0) - теги на записях и имена у источников, без реестра. Этот тест и есть реестр:
 * каждый пул, который называет источник, не пуст, каждый тег на записи кем-то назван, а
 * уникалка босса не состоит ни в одном чужом пуле. Mongo не нужна - только файлы содержимого.
 */
class PoolsTest {

    private data class Entry(val name: String, override val pools: Map<String, Int>) : Pooled

    private val definitions = ModifierSeeder.seedDefinitions() + UniqueEquipmentSeeder.seedDefinitions()
    private val equipment = EquipmentSeeder(definitions).seed()
    private val campaign = CampaignContent.file
    private val crafting = CraftsContent.file.crafting

    /** Пулы модификаторов предметов, которые называют источники. */
    private val modifierSources: Map<String, List<String>> =
        equipment.associate { "equipment ${it.code}" to it.modifierPools }.filterValues { it.isNotEmpty() } +
            EnumInfluence.entries.associate { "influence $it" to listOf(Pools.influence(it)) } +
            CurrencySeeder.records.values.filter { it.modifierPools.isNotEmpty() }.associate { "orb ${it.orb}" to it.modifierPools } +
            mapOf("smith" to crafting.modifierPools, "cartographer" to crafting.mapModifierPools)

    /** Пулы экипировки и уникалок, которые называют источники. */
    private val equipmentSources: Map<String, List<String>> =
        campaign.lootTables.flatMap { (name, table) -> table.drops.filter { it.equipmentPools.isNotEmpty() }.map { "loot $name" to it.equipmentPools } }.toMap() +
            campaign.monsters.filter { it.boss }.associate { "boss ${it.code}" to it.uniquePools } +
            CurrencySeeder.records.values.filter { it.uniquePools.isNotEmpty() }.associate { "orb ${it.orb}" to it.uniquePools } +
            mapOf("bosses" to campaign.bosses.uniquePools, "smith bases" to crafting.equipmentPools, "smith uniques" to crafting.uniquePools,
                "merchant" to MerchantRules.POOLS)

    private val monsterSources: Map<String, List<String>> = campaign.chapters.flatMap { it.maps }.associate { "map ${it.code}" to it.modifierPools }

    @Test
    fun the_first_pool_a_source_names_decides_the_weight_and_zero_excludes() {
        val entries = listOf(Entry("A", mapOf("x" to 10, "y" to 90)), Entry("B", mapOf("x" to 0, "y" to 50)), Entry("C", mapOf("y" to 5)))
        assertEquals(listOf("A" to 10, "C" to 5), Pools.of(entries, listOf("x", "y")).map { it.value.name to it.weight })
        assertEquals(listOf("A" to 90, "B" to 50, "C" to 5), Pools.of(entries, listOf("y", "x")).map { it.value.name to it.weight })
        assertTrue(Pools.of(entries, listOf("z")).isEmpty())
        val random = Random(3)
        val drawn = List(2000) { Pools.draw(Pools.of(entries, listOf("y")), random)!!.name }
        assertTrue(drawn.count { it == "C" } < drawn.count { it == "B" }, "a lighter entry is drawn less often")
    }

    @Test
    fun every_pool_a_source_names_has_members() {
        modifierSources.forEach { (source, tags) -> assertTrue(Pools.of(definitions, tags).isNotEmpty(), "$source names empty pools $tags") }
        equipmentSources.forEach { (source, tags) -> assertTrue(Pools.of(equipment, tags).isNotEmpty(), "$source names empty pools $tags") }
        monsterSources.forEach { (source, tags) -> assertTrue(Pools.of(campaign.modifiers, tags).isNotEmpty(), "$source names empty pools $tags") }
    }

    @Test
    fun every_pool_a_record_belongs_to_is_named_by_a_source() {
        fun orphans(records: List<Pair<String, Map<String, Int>>>, sources: Map<String, List<String>>): List<String> {
            val named = sources.values.flatten().toSet()
            return records.flatMap { (code, pools) -> pools.keys.filterNot { it in named }.map { "$code: $it" } }
        }
        assertEquals(emptyList(), orphans(definitions.map { it.code to it.pools }, modifierSources))
        assertEquals(emptyList(), orphans(equipment.map { it.code to it.pools }, equipmentSources))
        assertEquals(emptyList(), orphans(campaign.modifiers.map { it.code to it.pools }, monsterSources))
    }

    @Test
    fun a_boss_unique_drops_from_its_boss_alone() {
        val bossPools = campaign.monsters.filter { it.boss }.flatMap { it.uniquePools }.toSet()
        val own = equipment.filter { it.pools.keys.any { tag -> tag in bossPools } }
        assertTrue(own.isNotEmpty())
        own.forEach { assertEquals(1, it.pools.size, "${it.code} also sits in ${it.pools.keys}") }
    }

    @Test
    fun every_unique_sits_in_some_pool_and_ordinary_bases_never_in_a_unique_one() {
        equipment.filter { it.rarity == EnumRarity.UNIQUE }.forEach { assertTrue(it.pools.values.any { weight -> weight > 0 }, "${it.code} drops from nowhere") }
        equipment.filter { it.rarity != EnumRarity.UNIQUE }.forEach { base ->
            assertTrue(base.pools.keys.none { it.startsWith("unique:") || it.startsWith("boss:") }, "${base.code} sits in a unique pool")
        }
        assertTrue(CurrencySeeder.records.getValue(EnumCurrencyOrb.ORB_OF_CHANCE).uniquePools.isNotEmpty())
    }
}

import features.logic.campaign.CampaignContent
import features.logic.campaign.CampaignMapTemplate
import features.logic.campaign.CampaignMaps
import features.logic.campaign.WorldGraph
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Карта мира (0.67.0): зона открыта, когда пройдена хоть одна из тех, что ведут к ней. Mongo не нужна. */
class WorldGraphTest {

    private fun zone(code: String, level: Int, vararg from: String) =
        CampaignMapTemplate(code, "SHORE", level, 0, 0, from.toList(), monsters = emptyList(), count = emptyList(), chestLoot = "", boss = "", corrupted = "")

    private val graph = WorldGraph(listOf(zone("A", 1), zone("B", 3, "A"), zone("C", 3, "A"), zone("D", 5, "B", "C"), zone("E", 5, "C")))

    @Test
    fun any_passed_zone_that_leads_in_opens_it() {
        assertEquals(listOf("A"), graph.unlocked(emptyList()))
        assertEquals(listOf("A", "B", "C"), graph.unlocked(listOf("A")))
        assertEquals(listOf("A", "B", "C", "D"), graph.unlocked(listOf("A", "B")), "one of two ways in is enough")
        assertEquals(listOf("D", "E"), graph.next("C"))
    }

    @Test
    fun a_zone_no_link_reaches_is_found() {
        assertEquals(emptySet(), graph.unreachable())
        assertEquals(setOf("X", "Y"), WorldGraph(listOf(zone("A", 1), zone("X", 3, "Y"), zone("Y", 5, "X"))).unreachable())
    }

    @Test
    fun the_next_zone_map_is_one_the_links_lead_to() {
        val rule = CampaignContent.file.maps.copy(nextChance = 1.0)
        val next = graph.next("C")
        repeat(50) { assertTrue(CampaignMaps.drop(rule, 1.0, "C", next, Random(it)) in next) }
        assertEquals("E", CampaignMaps.drop(rule, 1.0, "E", graph.next("E"), Random(1)), "a dead end drops its own map")
    }

    @Test
    fun the_next_region_waits_for_the_finale() {
        val regions = CampaignContent.file.regions
        val first = regions.first().zones.map { it.code }
        val finale = regions.first().zones.single { it.finale }.code
        val second = regions[1].zones.map { it.code }.toSet()
        assertTrue(CampaignContent.unlocked(first - finale).none { it in second }, "the second region waits for the first finale")
        assertTrue(CampaignContent.unlocked(first).any { it in second }, "the first finale opens the second region")
    }
}

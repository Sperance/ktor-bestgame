import application.enums.EnumRarity
import config.PoolSeeder
import features.logic.atlas.AtlasBonuses
import features.logic.campaign.AbyssRifts
import features.logic.campaign.CampaignContent
import features.logic.campaign.HoardBonus
import features.logic.campaign.HoardRoll
import features.logic.pools.EnumPoolTarget
import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Бездна (0.72.0): расщелины по правилу и атласу, копилка по ступени, волны и вожаки на уровне ступени. */
class AbyssTest {

    private val rule = CampaignContent.file.abyss!!

    @Test
    fun cracks_open_from_their_level_and_never_lead_deeper_than_the_waves() {
        val random = Random(11)
        assertTrue(AbyssRifts.window(null, 0, rule, rule.minLevel - 1, random, AtlasBonuses(mapOf("ATLAS_ABYSS_CHANCE" to 100.0))).cracks.isEmpty())
        val atlas = AtlasBonuses(mapOf("ATLAS_ABYSS_CHANCE" to 100.0, "ATLAS_ABYSS_EXTRA" to 100.0, "ATLAS_ABYSS_DEPTH" to 5.0))
        val window = AbyssRifts.window(null, 0, rule, rule.minLevel, random, atlas)
        assertEquals(listOf(rule.waves.size, rule.waves.size), window.cracks, "two cracks, both as deep as the waves go")
        assertEquals(window, AbyssRifts.window(window, window.refreshAt - 1, rule, rule.minLevel, random, AtlasBonuses.NONE), "a live window stays")
        assertEquals(rule.waves.size, AbyssRifts.depth(rule, rule.depth[1], 9.0))
    }

    @Test
    fun the_hoard_holds_what_its_depth_shows_and_burns_on_death() {
        val random = Random(5)
        val bonus = HoardBonus(items = 1.5, rare = 10.0, orbs = 1.2, unique = 2.0, experience = 100.0)
        rule.waves.indices.map { it + 1 }.forEach { depth ->
            val view = AbyssRifts.view(rule, depth, bonus)
            repeat(200) {
                val hoard = AbyssRifts.roll(rule, depth, bonus, 1.0, random)
                assertTrue(hoard.items.size in view.items[0]..view.items[1], "items at depth $depth")
                assertTrue(hoard.orbs.values.sum().toInt() in view.orbs[0]..view.orbs[1], "orbs at depth $depth")
                assertTrue(hoard.items.all { it == EnumRarity.UNCOMMON || it == EnumRarity.RARE })
                assertTrue(hoard.orbs.keys.all { it in rule.orbs })
                assertEquals(view.experience, hoard.experience)
            }
        }
        assertEquals(HoardRoll.EMPTY, AbyssRifts.roll(rule, rule.waves.size, bonus, 0.0, random), "without the atlas a fall burns it all")
        assertEquals(HoardRoll.EMPTY, AbyssRifts.roll(rule, 0, bonus, 1.0, random), "no depth, no hoard")
    }

    @Test
    fun every_depth_brings_its_monsters_and_leaders_up_to_its_level() {
        val floors = CampaignContent.abyss(20, PoolSeeder.table(EnumPoolTarget.MONSTER), CampaignContent.seededModifiers)!!
        assertEquals(rule.waves.size, floors.floors.size)
        assertEquals(rule.waves.map { it.leader }, floors.floors.map { it.leader?.code })
        val first = floors.floors.first().monsters.first().stats.getValue("STOCK_HEALTH")
        val last = floors.floors.last().monsters.first().stats.getValue("STOCK_HEALTH")
        assertTrue(last > first, "the deepest wave is stronger: $first -> $last")
        floors.floors.mapNotNull { it.leader }.forEach { leader -> assertTrue(leader.modifiers.isNotEmpty() && leader.pool.isNotEmpty(), leader.code) }
        assertTrue(floors.modifiers.any { it.code.startsWith("ABYSS_MOB_") })
    }
}

import application.enums.EnumStatStock
import features.logic.atlas.AtlasBonuses
import features.logic.campaign.Crystal
import features.logic.campaign.EssenceCrystals
import features.logic.essences.EssenceContent
import features.logic.skills.SkillContent
import features.logic.skills.SkillKind
import features.logic.skills.SkillRules
import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Умения классов, книги и кристаллы эссенций (0.69.0): книга цела, требования и выпадение - по каталогу. */
class SkillsTest {

    private val book = SkillContent.book

    @Test
    fun every_class_opens_its_skills_on_the_catalog_ladder() {
        book.classes.forEach { heroClass ->
            val own = SkillContent.ofClass(heroClass.code)
            assertEquals(listOf(1, 6, 12, 20, 30, 40), own.filter { it.kind == SkillKind.ACTIVE }.map { it.unlock }, heroClass.code)
            assertEquals(listOf(1, 5, 10, 16, 24, 32, 42, 52), own.filter { it.kind == SkillKind.PASSIVE }.map { it.unlock }, heroClass.code)
            val starter = SkillRules.starter(heroClass.code)
            assertEquals(2, starter.learned.size, heroClass.code)
            assertTrue(starter.learned.values.all { it == 1 } && starter.active.size == 1 && starter.passive.size == 1, heroClass.code)
        }
    }

    @Test
    fun the_twentieth_level_asks_for_level_seventy_and_attributes_grow_with_it() {
        val spark = SkillContent.skills.getValue("SPARK")
        assertEquals(12, SkillRules.heroLevel(spark, 1))
        assertEquals(70, SkillRules.heroLevel(spark, SkillRules.MAX_LEVEL))
        val need = SkillRules.need(spark, 1)
        assertEquals(mapOf<application.enums.IntEnumStat, Int>(EnumStatStock.STOCK_INTELLECT to 35), need.attributes, "2.2 × 12 + 8")
        val scion = SkillRules.need(SkillContent.skills.getValue("ARC"), 1)
        assertEquals(3, scion.attributes.size, "all three attributes of the Scion")
        val stats = mapOf<application.enums.IntEnumStat, Double>(EnumStatStock.STOCK_INTELLECT to 34.0)
        assertTrue(SkillRules.unmet(spark, 1, 12, stats).isNotEmpty(), "one intelligence short")
        assertTrue(SkillRules.unmet(spark, 1, 12, stats + (EnumStatStock.STOCK_INTELLECT to 35.0)).isEmpty())
    }

    @Test
    fun a_book_never_drops_far_above_the_zone() {
        val random = Random(7)
        repeat(2000) {
            val code = SkillRules.dropBook("WITCH", 10, 1.0, 0.6, random) ?: return@repeat
            val skill = SkillContent.byBook(code)!!
            assertTrue(skill.unlock <= 10 + book.rules.books.reach, "$code at zone 10")
        }
    }

    @Test
    fun crystals_hold_essences_of_the_zone_and_a_vaal_orb_changes_them_once() {
        val rule = EssenceContent.book.crystals
        val random = Random(11)
        repeat(200) {
            val window = EssenceCrystals.window(null, 0, rule, 34, listOf("DROWNED"), random, AtlasBonuses.NONE)
            assertTrue(window.crystals.size in rule.count[0]..rule.count[1])
            window.crystals.flatMap { it.essences }.forEach { code ->
                val essence = EssenceContent.essences.getValue(code)
                assertTrue(essence.tier in EssenceContent.tierOf(34) - 1..EssenceContent.tierOf(34), code)
            }
        }
        val crystal = Crystal(listOf("ESSENCE_GREED_3", "ESSENCE_ANGER_7"), "DROWNED")
        repeat(100) {
            val (outcome, changed) = EssenceCrystals.vaal(crystal, rule, random)
            assertTrue(changed.vaal)
            when (outcome) {
                EssenceContent.VAAL_UPGRADE -> assertEquals(listOf("ESSENCE_GREED_4", "ESSENCE_ANGER_7"), changed.essences)
                EssenceContent.VAAL_SPECIAL -> assertEquals(1, changed.essences.count { EssenceContent.essences.getValue(it).special })
                else -> assertTrue(changed.stronger && changed.essences == crystal.essences)
            }
        }
    }
}

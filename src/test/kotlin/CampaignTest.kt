import application.enums.EnumRarity
import base.exception.model.CampaignExceptions
import config.CurrencySeeder
import features.logic.campaign.CampaignContent
import features.logic.campaign.CampaignLoot
import features.logic.campaign.EnumMonsterRarity
import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Кампания: содержимое главы и правила добычи. Mongo не нужна - файл читается из ресурсов,
 * а броски принимают [Random] с зерном.
 */
class CampaignTest {

    private val content = CampaignContent.file
    private val view = CampaignContent.view

    @Test
    fun the_first_chapter_is_ten_maps_growing_stronger() {
        val maps = view.chapters.first().maps
        assertEquals(10, maps.size)
        assertTrue(maps.zipWithNext().all { (a, b) -> a.level < b.level }, "уровни карт должны расти")
        assertEquals(1, maps.first().level)
        maps.forEach { assertTrue(it.monsters.size in 2..4, "${it.code}: ${it.monsters.size} монстров") }
        assertEquals(maps.size, maps.map { it.biome }.toSet().size, "у каждой карты свой биом")
    }

    @Test
    fun a_monster_is_stronger_on_a_deeper_map() {
        val first = view.chapters.first().maps.first { it.monsters.any { m -> m.code == "SKELETON_WARRIOR" } }
        val deeper = view.chapters.first().maps.last { it.monsters.any { m -> m.code == "SKELETON_WARRIOR" } }
        assertTrue(deeper.level > first.level)
        val life = { map: features.logic.campaign.CampaignMap -> map.monsters.first { it.code == "SKELETON_WARRIOR" }.stats.getValue("STOCK_HEALTH") }
        assertTrue(life(deeper) > life(first))
        // Скорость атаки не растёт с уровнем, а крит приходит из значений по умолчанию
        val speed = { map: features.logic.campaign.CampaignMap -> map.monsters.first { it.code == "SKELETON_WARRIOR" }.stats.getValue("STOCK_ATTACK_SPEED") }
        assertEquals(speed(first), speed(deeper))
        assertEquals(5.0, first.monsters.first().stats["STOCK_CRITICAL_CHANCE"])
    }

    @Test
    fun maps_open_one_after_another() {
        val codes = view.chapters.first().maps.map { it.code }
        assertEquals(listOf(codes[0]), CampaignContent.unlocked(emptyList()))
        assertEquals(codes.take(3), CampaignContent.unlocked(codes.take(2)))
    }

    @Test
    fun every_orb_in_a_loot_table_is_a_real_orb() {
        val orbs = CurrencySeeder.seed().map { it.code }.toSet()
        content.lootTables.values.flatMap { it.drops }.filter { it.code.isNotEmpty() }.forEach {
            assertTrue(it.code in orbs, "${it.code} нет в валюте")
        }
    }

    @Test
    fun rarer_monsters_give_more() {
        val monster = CampaignContent.monsters.getValue("DROWNED")
        val table = content.lootTables.getValue(monster.loot)
        val normal = content.rarities.first { it.rarity == EnumMonsterRarity.NORMAL }
        val rare = content.rarities.first { it.rarity == EnumMonsterRarity.RARE }

        assertTrue(CampaignLoot.experience(monster, 5, rare, 0.0) > CampaignLoot.experience(monster, 5, normal, 0.0))
        assertTrue(CampaignLoot.experience(monster, 10, normal, 0.0) > CampaignLoot.experience(monster, 5, normal, 0.0))

        fun drops(rarity: features.logic.campaign.CampaignRarity): Int {
            val random = Random(7)
            return (1..2000).sumOf { CampaignLoot.roll(table, 5, rarity, 0.0, 0.0, random).let { it.equipment + it.orbs.values.sum().toInt() } }
        }
        assertTrue(drops(rare) > drops(normal) * 3, "редкий монстр должен ронять заметно больше")
    }

    @Test
    fun a_rarity_bonus_shifts_equipment_towards_rarer_bases() {
        val bases = listOf(EnumRarity.COMMON, EnumRarity.RARE)
        fun rares(bonus: Double): Int {
            val random = Random(11)
            return (1..5000).count { CampaignLoot.pick(bases, { it }, bonus, random) == EnumRarity.RARE }
        }
        assertTrue(rares(200.0) > rares(0.0))
    }

    @Test
    fun broken_content_is_refused_at_start() {
        val text = javaClass.classLoader.getResource("content/${CampaignContent.FILE}")!!.readText()
        assertFailsWith<CampaignExceptions.CampaignException> {
            CampaignContent.load(text.replace("\"STOCK_ATTACK_SPEED\"", "\"STOCK_NOT_A_STAT\""))
        }
    }
}

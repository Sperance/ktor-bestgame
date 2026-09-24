import application.enums.EnumRarity
import base.exception.model.CampaignExceptions
import config.CurrencySeeder
import features.logic.campaign.CampaignContent
import features.logic.campaign.CampaignDeath
import features.logic.campaign.CampaignLoot
import features.logic.campaign.DeathRule
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
    fun a_rarity_tier_raises_every_growing_stat_and_opens_a_wider_stronger_pool() {
        val rarities = view.rarities.associateBy { it.rarity }
        val normal = rarities.getValue(EnumMonsterRarity.NORMAL)
        val magic = rarities.getValue(EnumMonsterRarity.MAGIC)
        val rare = rarities.getValue(EnumMonsterRarity.RARE)
        // Всё, что растёт с уровнем карты, растёт и с тиром редкости - и сильнее у старшего тира
        content.growth.keys.forEach { stat ->
            fun more(rarity: features.logic.campaign.CampaignRarity) = rarity.effects
                .filter { it.stat == stat && it.operation == application.enums.EnumModifierOperation.MORE }.sumOf { it.value }
            assertEquals(0.0, more(normal), stat)
            assertTrue(more(rare) > more(magic) && more(magic) > 0, stat)
        }
        assertTrue(rare.modifierPower > magic.modifierPower && magic.modifierPower >= normal.modifierPower)
        val magicPool = content.modifiers.count { it.minRarity <= EnumMonsterRarity.MAGIC }
        val rarePool = content.modifiers.count { it.minRarity <= EnumMonsterRarity.RARE }
        assertTrue(rarePool > magicPool, "у редкого монстра пул модификаторов должен быть шире")
        assertTrue(rare.quantity > magic.quantity && rare.rarityBonus > magic.rarityBonus && rare.experience > magic.experience)
    }

    @Test
    fun broken_content_is_refused_at_start() {
        val text = javaClass.classLoader.getResource("content/${CampaignContent.FILE}")!!.readText()
        assertFailsWith<CampaignExceptions.CampaignException> {
            CampaignContent.load(text.replace("\"STOCK_ATTACK_SPEED\"", "\"STOCK_NOT_A_STAT\""))
        }
    }

    @Test
    fun the_combat_rules_are_served_with_the_chapters_and_name_every_ailment_once() {
        val rules = view.combat
        assertTrue(rules.timeLimit > 0 && rules.unarmed.damage > 0 && rules.critical.multiplier >= 100)
        // Шесть недугов из EnumStatBool - по одному правилу на каждый, и каждый висит на своём типе урона
        val ailments = rules.ailments.map { it.ailment }
        assertEquals(ailments.toSet(), ailments.toSet().also { assertEquals(it.size, ailments.size) })
        assertEquals(setOf("BURNING", "CHILLED", "FROZEN", "SHOCKED", "POISONED", "BLEEDING"), ailments.toSet())
        assertTrue(rules.ailments.all { it.type.startsWith("STOCK_ATTACK_") && it.chance in 0.0..100.0 && it.duration > 0 })
        assertTrue(rules.ailments.single { it.ailment == "POISONED" }.stacks, "яд складывается стопками")
        assertTrue(rules.ailments.single { it.ailment == "FROZEN" }.threshold > 0, "заморозка - только сильным ударом")
        assertTrue(rules.flask.charges > 0 && rules.flask.heal in 1.0..100.0)
        assertTrue(rules.spell.castSpeed > 0 && rules.spell.manaCost in 1.0..100.0)
    }

    @Test
    fun a_casting_monster_has_mana_and_the_arcane_modifier_gives_both() {
        val casters = content.monsters.filter { (it.stats["STOCK_ATTACK_MAGICAL"] ?: 0.0) > 0 }
        assertTrue(casters.size >= 3, "в главе должны быть колдующие монстры")
        casters.forEach { assertTrue((it.stats["STOCK_MANA"] ?: 0.0) > 0 && (it.stats["STOCK_CAST_SPEED"] ?: 0.0) > 0, it.code) }
        val arcane = content.modifiers.single { it.code == "MOB_ARCANE" }
        assertEquals(EnumMonsterRarity.RARE, arcane.minRarity)
        assertEquals(setOf("STOCK_ATTACK_MAGICAL", "STOCK_MANA", "STOCK_CAST_SPEED"), arcane.effects.map { it.stat }.toSet())
        // Урон заклинаний и мана растут с уровнем карты, как здоровье
        val acolyte = { map: features.logic.campaign.CampaignMap -> map.monsters.firstOrNull { it.code == "ABYSSAL_ACOLYTE" } }
        val temple = view.chapters.first().maps.last { acolyte(it) != null }
        assertTrue(acolyte(temple)!!.stats.getValue("STOCK_ATTACK_MAGICAL") > 4.5)
    }

    @Test
    fun death_costs_a_share_of_the_level_and_never_the_level_itself() {
        val rule = DeathRule(fromLevel = 10, experienceShare = 5.0)
        // Ранняя карта - бесплатно; последний уровень - нечего терять
        assertEquals(0.0, CampaignDeath.lost(rule, 5, 1500.0, 1000.0, 2000.0))
        assertEquals(0.0, CampaignDeath.lost(rule, 12, 1500.0, 1000.0, null))
        // Пять процентов от шага уровня...
        assertEquals(50.0, CampaignDeath.lost(rule, 12, 1500.0, 1000.0, 2000.0))
        // ...но не ниже его порога
        assertEquals(20.0, CampaignDeath.lost(rule, 12, 1020.0, 1000.0, 2000.0))
        assertEquals(0.0, CampaignDeath.lost(rule, 12, 1000.0, 1000.0, 2000.0))
        assertEquals(0.0, CampaignDeath.lost(DeathRule(1, 0.0), 12, 1500.0, 1000.0, 2000.0))
    }

    @Test
    fun broken_combat_rules_are_refused_at_start() {
        val text = javaClass.classLoader.getResource("content/${CampaignContent.FILE}")!!.readText()
        assertFailsWith<CampaignExceptions.CampaignException> { CampaignContent.load(text.replace("\"BURNING\"", "\"SLEEPING\"")) }
        assertFailsWith<CampaignExceptions.CampaignException> { CampaignContent.load(text.replace("\"resistCap\": 75", "\"resistCap\": 175")) }
        // Колдун без маны - ошибка файла
        assertFailsWith<CampaignExceptions.CampaignException> { CampaignContent.load(text.replace("\"STOCK_MANA\": 40", "\"STOCK_MANA\": 0")) }
    }

    @Test
    fun every_monster_walks_by_a_rule_and_every_biome_has_its_light() {
        view.chapters.flatMap { it.maps }.forEach { map ->
            assertTrue(map.light > 0, "${map.code}: свет ${map.light}")
            map.monsters.forEach { monster ->
                assertTrue(monster.behaviour.type in features.logic.campaign.BehaviourRule.types, "${monster.code}: ${monster.behaviour.type}")
                assertEquals(content.behaviour.forms[monster.form] ?: content.behaviour.default, monster.behaviour, monster.code)
            }
        }
        // Каждая форма, что есть в кампании, описана сама - иначе все звери ходили бы как люди.
        content.monsters.map { it.form }.toSet().forEach { assertTrue(it in content.behaviour.forms, "форма $it без поведения") }
        assertTrue(content.behaviour.forms.values.any { it.type == "AMBUSH" } && content.behaviour.forms.values.any { it.type == "SLEEP" })
    }

    @Test
    fun a_chest_window_lives_six_hours_and_the_bonus_adds_chests() {
        val rule = content.chests
        val now = 1_000_000L
        val first = features.logic.campaign.CampaignChests.window(null, now, rule, 0.0, Random(1))
        assertTrue(first.left in rule.count[0]..rule.count[1])
        assertEquals(now + 6 * 3_600_000L, first.refreshAt)
        // Inside the window it stays as it is, whatever was opened.
        val opened = first.copy(left = 0)
        assertEquals(opened, features.logic.campaign.CampaignChests.window(opened, first.refreshAt - 1, rule, 500.0, Random(2)))
        // After it a new window is rolled; 200% chest quantity is always two more.
        repeat(20) { seed ->
            val next = features.logic.campaign.CampaignChests.window(opened, first.refreshAt, rule, 200.0, Random(seed))
            assertTrue(next.left in rule.count[0] + 2..rule.count[1] + 2, "$next")
        }
        view.chapters.flatMap { it.maps }.forEach { map ->
            assertTrue(content.chapters.flatMap { it.maps }.first { it.code == map.code }.chestLoot in content.lootTables)
        }
    }
}

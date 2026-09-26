import features.logic.crafts.Crafts
import features.logic.crafts.CraftsContent
import features.logic.crafts.ProfessionProgress
import features.logic.crafts.WorkBonus
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ремёсла (0.37.0): файл профессий и правила досчёта. Mongo не нужна - файлы читаются из
 * ресурсов, а броски принимают [Random] с зерном.
 */
class CraftsTest {

    private val content = CraftsContent.file
    private val rules = content.rules

    private fun records(file: String, section: String) =
        kotlinx.serialization.json.Json.parseToJsonElement(config.ContentResource.read(file)).jsonObject.getValue(section).jsonArray.map { it.jsonObject }

    @Test
    fun every_work_yields_a_real_material_and_every_profession_has_a_starter_tool() {
        val items = records("items.json", "items").associate { it.getValue("code").jsonPrimitive.content to it.getValue("category").jsonPrimitive.content }
        val orbs = records("currency.json", "currency").map { it.getValue("orb").jsonPrimitive.content }.toSet()
        val maps = features.logic.campaign.CampaignContent.mapCodes
        // Сгущение эссенций (0.69.0): эссенция и на входе, и на выходе.
        val stacked = setOf("MATERIAL", "STONE_STOCK", "WOOD_STOCK", "ESSENCE")
        val flasks = records("equipment.json", "equipment").filter { it.getValue("slot").jsonPrimitive.content == "FLASK" }.map { it.getValue("code").jsonPrimitive.content }.toSet()
        content.professions.flatMap { it.jobs }.forEach { job ->
            when (job.kind) {
                features.logic.crafts.JobKind.ITEM -> assertTrue(items[job.output] in stacked || job.output in orbs, "${job.code}: ${job.output}")
                features.logic.crafts.JobKind.EQUIPMENT -> assertTrue(job.band.size == 2, job.code)
                features.logic.crafts.JobKind.MAP -> assertTrue(job.map in maps, "${job.code}: ${job.map}")
                features.logic.crafts.JobKind.FLASK -> assertTrue(job.output in flasks, "${job.code}: ${job.output}")
                features.logic.crafts.JobKind.BOOK -> assertTrue(features.logic.skills.SkillContent.ofClass(job.output).any { it.unlock <= job.band.single() }, job.code)
            }
            job.extra.forEach { assertEquals("MATERIAL", items[it.item], "${job.code}: ${it.item}") }
            job.inputs.forEach { assertTrue(items[it.item] in stacked, "${job.code}: ${it.item}") }
        }
        content.crafting.additives.forEach { (item, _) -> assertEquals("MATERIAL", items[item], item) }
        val tools = records("equipment.json", "equipment").groupBy { it.getValue("slot").jsonPrimitive.content }
        content.professions.forEach { profession ->
            val own = tools[profession.tool.name].orEmpty().map { it.getValue("code").jsonPrimitive.content }
            assertTrue(own.any { it.startsWith("BRONZE_") }, "${profession.code}: нет стартового инструмента")
        }
    }

    @Test
    fun a_work_pays_by_its_cycle_and_a_missed_cycle_gives_nothing() {
        val job = content.professions.first().jobs.first { it.level == 1 }.copy(nothing = 0.0, extra = emptyList())
        val cycle = Crafts.cycleMillis(rules, job, 1, WorkBonus())
        assertEquals((job.seconds * 1000).toLong(), cycle)
        val done = Crafts.settle(rules, job, ProfessionProgress(), WorkBonus(), 0, cycle * 10 + cycle / 2, 1L, 0)
        assertEquals(10, done.gains.cycles)
        assertEquals(10L, done.gains.items[job.output])
        assertEquals(cycle * 10, done.settledAt, "полцикла остаётся до следующего обращения")
        val empty = Crafts.settle(rules, job.copy(nothing = 100.0), ProfessionProgress(), WorkBonus(), 0, cycle * 10, 1L, 0)
        assertEquals(10, empty.gains.nothing)
        assertTrue(empty.gains.items.isEmpty())
    }

    @Test
    fun bonuses_speed_the_cycle_add_units_and_cut_the_nothing() {
        val job = content.professions.first().jobs.first { it.level == 1 }
        assertTrue(Crafts.cycleMillis(rules, job, 1, WorkBonus(speed = 100.0)) * 2 <= Crafts.cycleMillis(rules, job, 1, WorkBonus()) + 1)
        assertTrue(Crafts.cycleMillis(rules, job, rules.maxLevel, WorkBonus()) < Crafts.cycleMillis(rules, job, 1, WorkBonus()), "уровень ускоряет")
        assertEquals(job.nothing / 2, Crafts.nothingChance(rules, job, WorkBonus(luck = 50.0)), 1e-9)
        val sure = job.copy(nothing = 0.0, extra = emptyList())
        val cycle = Crafts.cycleMillis(rules, sure, 1, WorkBonus(yield = 100.0))
        assertEquals(20L, Crafts.settle(rules, sure, ProfessionProgress(), WorkBonus(yield = 100.0), 0, cycle * 10, 3L, 0).gains.items[sure.output])
    }

    @Test
    fun offline_counts_only_up_to_the_cap_and_levels_rise_on_the_way() {
        val job = content.professions.first().jobs.first { it.level == 1 }.copy(nothing = 0.0, experience = 1000.0)
        val day = 24 * 3_600_000L
        val done = Crafts.settle(rules, job, ProfessionProgress(), WorkBonus(), 0, day, 5L, 0)
        assertEquals(day, done.settledAt, "простой дольше потолка не копится")
        val capped = (rules.offlineHours * 3_600_000).toLong()
        assertTrue(done.gains.cycles.toLong() * Crafts.cycleMillis(rules, job, rules.maxLevel, WorkBonus()) <= capped + Crafts.cycleMillis(rules, job, 1, WorkBonus()))
        assertTrue(done.progress.level > 1)
        assertTrue(done.progress.level <= rules.maxLevel)
    }

    @Test
    fun a_craft_spends_every_cycle_and_stops_when_the_bag_runs_dry() {
        val job = content.professions.first { it.code == "SMITHING" }.jobs.first { it.level == 1 }.copy(nothing = 0.0)
        val cycle = Crafts.cycleMillis(rules, job, 1, WorkBonus())
        val need = job.inputs.single().amount
        val done = Crafts.settle(rules, job, ProfessionProgress(), WorkBonus(), 0, cycle * 10, 2L, 0, mapOf(job.inputs.single().item to need * 3 + 1))
        assertEquals(3, done.gains.cycles)
        assertEquals(3, done.gains.made)
        assertEquals(need * 3, done.gains.spent[job.inputs.single().item])
        assertTrue(done.gains.starved)
        val withFlux = Crafts.perCycle(job, listOf("FIRE_FLUX"))
        assertEquals(1L, withFlux["FIRE_FLUX"])
    }

    /** Цикл бросается от зерна и своего номера (0.42.0): досчёт по частям даёт то же, что и целиком. */
    @Test
    fun a_cycle_is_its_seed_and_index_however_the_settling_is_split() {
        val job = content.professions.first().jobs.first { it.level == 1 }
        val cycle = Crafts.cycleMillis(rules, job, 1, WorkBonus())
        val whole = Crafts.settle(rules, job, ProfessionProgress(), WorkBonus(yield = 30.0), 0, cycle * 12, 42L, 0)
        val first = Crafts.settle(rules, job, ProfessionProgress(), WorkBonus(yield = 30.0), 0, cycle * 5, 42L, 0)
        val second = Crafts.settle(rules, job, first.progress, WorkBonus(yield = 30.0), first.settledAt, cycle * 12, 42L, first.gains.cycles.toLong())
        val joined = (first.gains.items.keys + second.gains.items.keys).associateWith { (first.gains.items[it] ?: 0) + (second.gains.items[it] ?: 0) }
        assertEquals(whole.gains.items, joined)
        assertEquals(whole.gains.nothing, first.gains.nothing + second.gains.nothing)
    }

    /** Клиент бросает цикл тем же генератором: то же число стоит в клиентском CraftCycleTest. */
    @Test
    fun the_cycle_generator_is_the_one_the_client_uses() {
        assertEquals(0.9186378747095982, Crafts.cycleRandom(42, 3).nextDouble())
    }
}

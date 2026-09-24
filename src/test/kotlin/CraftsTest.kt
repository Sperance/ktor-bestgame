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
        content.professions.flatMap { it.jobs }.forEach { job ->
            assertEquals("MATERIAL", items[job.output], "${job.code}: ${job.output}")
            job.extra.forEach { assertEquals("MATERIAL", items[it.item], "${job.code}: ${it.item}") }
        }
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
        val done = Crafts.settle(rules, job, ProfessionProgress(), WorkBonus(), 0, cycle * 10 + cycle / 2, Random(1))
        assertEquals(10, done.gains.cycles)
        assertEquals(10L, done.gains.items[job.output])
        assertEquals(cycle * 10, done.settledAt, "полцикла остаётся до следующего обращения")
        val empty = Crafts.settle(rules, job.copy(nothing = 100.0), ProfessionProgress(), WorkBonus(), 0, cycle * 10, Random(1))
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
        assertEquals(20L, Crafts.settle(rules, sure, ProfessionProgress(), WorkBonus(yield = 100.0), 0, cycle * 10, Random(3)).gains.items[sure.output])
    }

    @Test
    fun offline_counts_only_up_to_the_cap_and_levels_rise_on_the_way() {
        val job = content.professions.first().jobs.first { it.level == 1 }.copy(nothing = 0.0, experience = 1000.0)
        val day = 24 * 3_600_000L
        val done = Crafts.settle(rules, job, ProfessionProgress(), WorkBonus(), 0, day, Random(5))
        assertEquals(day, done.settledAt, "простой дольше потолка не копится")
        val capped = (rules.offlineHours * 3_600_000).toLong()
        assertTrue(done.gains.cycles.toLong() * Crafts.cycleMillis(rules, job, rules.maxLevel, WorkBonus()) <= capped + Crafts.cycleMillis(rules, job, 1, WorkBonus()))
        assertTrue(done.progress.level > 1)
        assertTrue(done.progress.level <= rules.maxLevel)
    }
}

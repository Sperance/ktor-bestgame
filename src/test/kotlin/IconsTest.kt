import config.ContentStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Правило владельца (1.35.2): у каждого предмета - шаблона экипировки и предмета сумки - есть иконка с живым спрайтом,
 * а у каждой уникальной и мифической вещи спрайт свой: его не носит ни один другой предмет.
 * Так же у каждого стата героя, боя, фляги и ауры - свой спрайт, не делимый ни с одним другим ключом.
 */
class IconsTest {
    private val index = ContentStore.load().index
    private val doc = Json.parseToJsonElement(javaClass.getResource("/icons/icons.json")!!.readText()).jsonObject
    private val icons = doc.getValue("icons").jsonObject.mapValues { it.value.jsonPrimitive.content }
    private val spriteDocs = doc.getValue("sprites").jsonObject
    private val sprites = spriteDocs.keys

    @Test
    fun every_item_has_an_icon_with_a_sprite() {
        val keys = index.templates.keys.map { "equipment.$it" } + index.items.keys.map { "item.$it" }
        val broken = keys.filter { icons[it]?.let(sprites::contains) != true }
        assertTrue(broken.isEmpty(), "нет иконки или спрайта: ${broken.size}, например ${broken.take(10)}")
    }

    @Test
    fun every_unique_has_its_own_sprite() {
        val owners = icons.filterKeys { it.startsWith("equipment.") || it.startsWith("item.") }.entries.groupBy({ it.value }, { it.key })
        val shared = index.templates.values.filter { it.unique }.map { "equipment.${it.code}" }
            .filter { key -> (owners[icons[key]]?.size ?: 0) > 1 }
        assertTrue(shared.isEmpty(), "уникалка делит спрайт: ${shared.size}, например ${shared.take(10)}")
    }

    @Test
    fun every_hero_stat_has_its_own_sprite() {
        val groups = setOf("HERO", "BATTLE", "FLASK", "AURA")
        val stats = Json.parseToJsonElement(javaClass.getResource("/content/stats.json")!!.readText()).jsonObject
            .getValue("stats").jsonArray.map { it.jsonObject }
            .filter { it.getValue("group").jsonPrimitive.content in groups }
            .map { "stat.${it.getValue("code").jsonPrimitive.content}" }
        val owners = icons.values.groupingBy { it }.eachCount()
        val broken = stats.filter { key -> icons[key]?.takeIf(sprites::contains)?.let { owners[it] == 1 } != true }
        assertTrue(stats.isNotEmpty() && broken.isEmpty(), "стат без своего спрайта: ${broken.size}, например ${broken.take(10)}")
    }

    @Test
    fun every_sprite_path_is_valid_path_data() {
        val broken = spriteDocs.flatMap { (name, sprite) ->
            sprite.jsonObject.getValue("paths").jsonArray.map { it.jsonObject.getValue("d").jsonPrimitive.content }
                .filterNot(::isPathData).map { name }
        }.distinct()
        assertTrue(broken.isEmpty(), "спрайт с битым контуром: ${broken.size}, например ${broken.take(10)}")
    }

    /** SVG path data: starts with a move, every command gets a whole number of its argument groups. */
    private fun isPathData(d: String): Boolean {
        val tokens = PATH_TOKEN.findAll(d).map { it.value }.toList()
        if (tokens.joinToString("").length != d.filterNot { it.isWhitespace() || it == ',' }.length) return false
        if (tokens.firstOrNull()?.uppercase() != "M") return false
        var arity = 0
        var args = 0
        for (token in tokens) {
            val command = ARITY[token.uppercase()]
            if (command == null) { if (arity == 0) return false; args++; continue }
            if (arity > 0 && (args == 0 || args % arity != 0)) return false
            if (arity == 0 && args > 0) return false
            arity = command
            args = 0
        }
        return arity == 0 && args == 0 || arity > 0 && args > 0 && args % arity == 0
    }

    private companion object {
        val PATH_TOKEN = Regex("[MmLlHhVvCcSsQqTtAaZz]|[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?")
        val ARITY = mapOf("M" to 2, "L" to 2, "H" to 1, "V" to 1, "C" to 6, "S" to 4, "Q" to 4, "T" to 2, "A" to 7, "Z" to 0)
    }
}

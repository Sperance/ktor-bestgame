import config.ContentStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Правило владельца (1.35.2): у каждого предмета - шаблона экипировки и предмета сумки - есть иконка с живым спрайтом,
 * а у каждой уникальной и мифической вещи спрайт свой: его не носит ни один другой предмет.
 */
class IconsTest {
    private val index = ContentStore.load().index
    private val doc = Json.parseToJsonElement(javaClass.getResource("/icons/icons.json")!!.readText()).jsonObject
    private val icons = doc.getValue("icons").jsonObject.mapValues { it.value.jsonPrimitive.content }
    private val sprites = doc.getValue("sprites").jsonObject.keys

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
}

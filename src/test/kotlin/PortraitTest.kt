import config.ProgressionSeeder
import features.logic.campaign.CampaignContent
import features.logic.portraits.PortraitCache
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Портреты (с 0.29.0): у каждого класса и у каждой формы монстра есть свой файл, и каждый файл
 * обходится тем подмножеством SVG, которое клиент рисует сам. Mongo не нужна.
 */
class PortraitTest {

    private val elements = setOf("svg", "defs", "linearGradient", "radialGradient", "stop", "g", "path", "circle", "ellipse", "rect")
    private val attributes = setOf(
        "xmlns", "viewBox", "width", "height", "id", "gradientUnits", "x1", "y1", "x2", "y2", "cx", "cy", "r", "rx", "ry", "x", "y",
        "offset", "stop-color", "stop-opacity", "d", "fill", "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin",
        "opacity", "fill-opacity", "stroke-opacity",
    )

    init { PortraitCache.initializeCache() }

    @Test
    fun every_class_and_every_monster_form_has_a_portrait() {
        val keys = PortraitCache.keys()
        ProgressionSeeder.classCodes.forEach { assertTrue("class.$it" in keys, "нет портрета класса $it") }
        CampaignContent.monsters.values.map { it.form }.distinct().forEach { assertTrue("form.$it" in keys, "нет портрета формы $it") }
        assertEquals(keys, PortraitCache.manifest().portraits.keys)
    }

    @Test
    fun every_file_on_disk_is_reached_by_a_code() {
        val root = File(javaClass.classLoader.getResource(PortraitCache.FOLDER)!!.toURI())
        val files = root.walkTopDown().filter { it.isFile }.map { "${it.parentFile.name}.${it.nameWithoutExtension}" }.toSet()
        assertEquals(files, PortraitCache.keys(), "файл без кода или код без файла")
    }

    @Test
    fun every_portrait_is_three_by_four_and_uses_only_what_the_client_draws() {
        PortraitCache.keys().forEach { key ->
            val text = PortraitCache.document(key.substringBefore('.'), key.substringAfter('.'))!!
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(text.byteInputStream()).documentElement
            assertEquals("0 0 ${PortraitCache.WIDTH} ${PortraitCache.HEIGHT}", document.getAttribute("viewBox"), key)
            val ids = mutableSetOf<String>()
            val references = mutableSetOf<String>()
            fun walk(element: Element) {
                assertTrue(element.tagName in elements, "$key: <${element.tagName}>")
                for (i in 0 until element.attributes.length) {
                    val attribute = element.attributes.item(i)
                    assertTrue(attribute.nodeName in attributes, "$key: ${attribute.nodeName} на <${element.tagName}>")
                    Regex("url\\(#([^)]+)\\)").find(attribute.nodeValue)?.let { references += it.groupValues[1] }
                }
                if (element.tagName.endsWith("Gradient")) {
                    ids += element.getAttribute("id")
                    assertEquals("userSpaceOnUse", element.getAttribute("gradientUnits"), "$key: ${element.getAttribute("id")}")
                }
                val children = element.childNodes
                for (i in 0 until children.length) (children.item(i) as? Element)?.let(::walk)
            }
            walk(document)
            assertTrue(ids.containsAll(references), "$key: ссылка на несуществующий градиент ${references - ids}")
        }
    }
}

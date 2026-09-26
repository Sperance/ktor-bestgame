import application.enums.EnumModifierSource
import application.enums.EnumRarity
import config.EquipmentSeeder
import config.ModifierSeeder
import config.PoolSeeder
import config.UniqueEquipmentSeeder
import features.logic.modifiers.ModifierRoller
import features.logic.pools.EnumPoolTarget
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Дно редкости (0.66.4): волшебный предмет несёт хотя бы один аффикс, редкий - не меньше дна своей
 * редкости. Аффиксы - строки, которые клиент рисует голубым под базой предмета; имплисит, порча и
 * зачарование в счёт не идут. Доводку до дна делает [ModifierRoller.ensureAffixes] на каждой записи
 * копии, но она берёт аффиксы только из пулов шаблона: если там меньше семейств, чем требует дно,
 * предмет так и остаётся ниже. Тест держит справочник так, чтобы дно было достижимо у каждого
 * шаблона, - без базы, по файлам содержимого.
 */
class RarityFloorTest {

    private val definitions = ModifierSeeder.seedDefinitions() + UniqueEquipmentSeeder.seedDefinitions()

    /** Всё, что катают ролл и сферы: аффиксы с тирами, не ремесленные (как `affixPool` кеша). */
    private val affixes = definitions.filter { it.isAffix() && !it.crafted && it.tiers.isNotEmpty() }

    private val table = PoolSeeder.table(EnumPoolTarget.MODIFIER)

    /** Шаблоны, что бывают волшебными и редкими: уникалки и мифические держат свои строки и не роллят. */
    private val templates = EquipmentSeeder(definitions).seed().filterNot { it.rarity.fixed }

    @Test
    fun every_template_can_reach_the_floor_of_magic_and_rare() {
        assertTrue(templates.size > 100, "шаблонов почти нет (${templates.size}) - тест ничего не проверил")
        val short = templates.flatMap { template ->
            val pool = table.of(affixes, template.modifierPools)
            listOf(EnumRarity.UNCOMMON, EnumRarity.RARE).mapNotNull { rarity ->
                val limits = rarity.limits(template.slot)
                // Два аффикса одного семейства на предмет не встают: место даёт только новое семейство своего вида.
                fun reach(source: EnumModifierSource, places: Int) =
                    minOf(places, pool.filter { it.value.source == source }.map { it.value.family() }.distinct().size)
                val reach = reach(EnumModifierSource.PREFIX, limits.prefixes) + reach(EnumModifierSource.SUFFIX, limits.suffixes)
                "${template.code} ${rarity.name}: $reach < ${limits.affixes.first}".takeIf { reach < limits.affixes.first }
            }
        }
        assertTrue(short.isEmpty(), "дно редкости недостижимо (${short.size}): ${short.take(25)}")
    }

    @Test
    fun a_fresh_roll_lands_on_the_floor_or_above() {
        val short = templates.flatMap { template ->
            val pool = table.of(affixes, template.modifierPools)
            listOf(EnumRarity.UNCOMMON, EnumRarity.RARE).flatMap { rarity ->
                val limits = rarity.limits(template.slot)
                // Как rollAffixes: число аффиксов из диапазона редкости, места - по видам.
                List(40) { ModifierRoller.pickAffixes(pool, limits.prefixes, limits.suffixes, limit = limits.affixes.random()).size }
                    .filter { it < limits.affixes.first }.distinct().map { "${template.code} ${rarity.name}: $it" }
            }
        }
        assertTrue(short.isEmpty(), "свежий ролл ниже дна (${short.size}): ${short.take(25)}")
    }
}

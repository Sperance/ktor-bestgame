import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.roll.ItemInstance
import config.ContentStore
import features.data.hero.Hero
import features.logic.hero.Stash
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Тайник (1.1.0): вещей не больше мест, лишнее ждёт в переполнении, сверх него продаётся; id у героя единственны. */
class StashTest {
    private val index = ContentStore.load().index
    private val rules = index.rules.stash
    private val template = index.templates.values.first { !it.unique }

    private fun item(id: String) = ItemInstance(id, template.code, Rarity.COMMON)

    @Test
    fun a_full_stash_overflows_and_then_sells() {
        val hero = Hero(userId = "u", name = "n")
        val capacity = Stash.capacity(hero, index)
        val arrived = Stash.receive(hero, List(capacity + rules.overflowSlots + 2) { item("i$it") }, index)
        assertEquals(capacity, hero.items.size)
        assertEquals(rules.overflowSlots, hero.overflow.size)
        assertEquals(2, arrived.sold)
        assertTrue(arrived.gold > 0 && hero.money == arrived.gold)

        Stash.receive(hero, List(rules.slotStep) { item("x$it") }, index)
        hero.money = rules.price(0)
        Stash.expand(hero, index)
        assertEquals(0, hero.money)
        assertEquals(rules.slotStep, Stash.claim(hero, null, index))
        assertEquals(capacity + rules.slotStep, hero.items.size)
    }

    @Test
    fun a_repeated_id_gets_a_fresh_one() {
        val hero = Hero(userId = "u", name = "n")
        Stash.receive(hero, listOf(item("same"), item("same")), index)
        assertEquals(2, hero.items.map { it.id }.toSet().size)
    }

    @Test
    fun the_stash_never_grows_past_its_ceiling() {
        val hero = Hero(userId = "u", name = "n", stashSlots = 10_000)
        assertEquals(rules.maxSlots, Stash.capacity(hero, index))
        assertTrue(rules.maxSlots <= com.sperance.exileforge.rules.content.StashRules.HARD_CAP)
        assertEquals(0L, rules.price(hero.stashSlots))
    }
}

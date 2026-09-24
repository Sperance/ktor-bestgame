import application.enums.EnumEquipmentType
import application.enums.EnumRarity
import config.EquipmentSeeder
import config.ModifierSeeder
import config.UniqueEquipmentSeeder
import features.data.auction.AuctionSlots
import features.logic.trade.MerchantRules
import features.logic.trade.MerchantStock
import features.logic.trade.SellPrice
import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Торговец и места под лоты (0.34.0): правила чистые, Mongo не нужна - роллы подменяются пустыми.
 */
class MerchantTest {

    private val equipment = EquipmentSeeder(ModifierSeeder.seedDefinitions() + UniqueEquipmentSeeder.seedDefinitions()).seed()

    @Test
    fun the_merchant_lays_out_four_to_six_magic_or_rare_items_near_the_hero() {
        repeat(30) { seed ->
            val stock = MerchantRules.stock(null, "hero", 10, 0L, equipment, Random(seed)) { _, _ -> mutableListOf() }
            assertTrue(stock.offers.size in MerchantRules.MIN_OFFERS..MerchantRules.MAX_OFFERS, "${stock.offers.size}")
            assertEquals(4 * 3_600_000L, stock.refreshAt)
            stock.offers.forEach { offer ->
                val template = equipment.first { it._id == offer.item.equipmentId }
                assertTrue(template.requiredLevel in 8..12, "${template.code}: ${template.requiredLevel}")
                assertTrue(template.rarity != EnumRarity.UNIQUE && template.slot != EnumEquipmentType.JEWEL)
                assertTrue(offer.item.rarity in setOf(EnumRarity.COMMON, EnumRarity.UNCOMMON, EnumRarity.RARE))
                assertEquals(SellPrice.of(template, offer.item.rarity, offer.item.params, emptyMap()) * 4, offer.price)
            }
        }
    }

    @Test
    fun a_stock_stays_until_its_window_ends() {
        val stock = MerchantRules.stock(null, "hero", 10, 0L, equipment, Random(1)) { _, _ -> mutableListOf() }
        assertSame(stock, MerchantRules.stock(stock, "hero", 10, stock.refreshAt - 1, equipment, Random(2)) { _, _ -> mutableListOf() })
        val next: MerchantStock = MerchantRules.stock(stock, "hero", 10, stock.refreshAt, equipment, Random(2)) { _, _ -> mutableListOf() }
        assertTrue(next.refreshAt > stock.refreshAt)
    }

    @Test
    fun lot_places_start_at_five_and_grow_by_half_again_up_to_twenty() {
        assertEquals(listOf(500L, 750L, 1125L), (0..2).map(AuctionSlots::price))
        assertEquals(5, AuctionSlots.of(0, 0).limit)
        assertEquals(0L, AuctionSlots.of(15, 3).price, "twenty is the ceiling")
    }
}

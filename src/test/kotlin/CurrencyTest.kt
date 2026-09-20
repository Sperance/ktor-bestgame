import application.enums.EnumCurrencyOrb
import application.enums.EnumEquipmentType
import application.enums.EnumRarity
import config.CurrencySeeder
import features.data.equipment.equipment_data.Armor
import features.data.equipment.equipment_data.Equipment
import features.data.inventory.CharacterEquipment
import features.logic.currency.CurrencyApplier
import org.junit.Test

/**
 * Данные валютных сфер и те их правила, которые отсекают предмет
 * до любых обращений к справочникам - поэтому Mongo здесь не нужна.
 */
class CurrencyTest {

    private val orbs = CurrencySeeder.seed()

    private fun template(rarity: EnumRarity = EnumRarity.COMMON) = Armor(
        slot = EnumEquipmentType.HELMET,
        name = "Test Helm",
        rarity = rarity,
        itemLevel = 50
    )

    private fun item(rarity: EnumRarity, corrupted: Boolean = false) = CharacterEquipment(
        characterId = "character",
        equipmentId = "equipment",
        rarity = rarity,
        corrupted = corrupted
    )

    // ==================== Данные ====================

    @Test
    fun every_orb_has_an_item_document() {
        val seeded = orbs.mapNotNull { EnumCurrencyOrb.byCode(it.subCategory) }.toSet()
        assert(seeded == EnumCurrencyOrb.entries.toSet()) {
            "Orbs without an item document: ${EnumCurrencyOrb.entries.toSet() - seeded}"
        }
    }

    @Test
    fun orb_items_are_currency_with_unique_stable_ids() {
        assert(orbs.all { it.category == EnumCurrencyOrb.CATEGORY }) { "Some orbs are not in the currency category" }
        assert(orbs.map { it._id }.toSet().size == orbs.size) { "Duplicate orb ids" }
        assert(orbs.map { it.name }.toSet().size == orbs.size) { "Duplicate orb names" }
        assert(orbs.all { it.price > 0 }) { "Some orbs have no price" }

        val again = CurrencySeeder.seed().associate { it.subCategory to it._id }
        assert(again == orbs.associate { it.subCategory to it._id }) { "Orb ids changed between seeds" }
    }

    @Test
    fun rarity_ladder_grows_with_rarity() {
        val ladder = listOf(EnumRarity.COMMON, EnumRarity.UNCOMMON, EnumRarity.RARE, EnumRarity.EPIC, EnumRarity.MYTHICAL)

        ladder.zipWithNext { lower, higher ->
            assert(higher.affixCount() >= lower.affixCount()) {
                "$higher holds fewer affixes than $lower"
            }
        }
        assert(EnumRarity.COMMON.affixCount() == 0) { "COMMON must carry no affixes, as in POE" }
        assert(EnumRarity.UNIQUE.affixCount() == 0) { "UNIQUE must not roll affixes" }
    }

    // ==================== Правила ====================

    @Test
    fun corrupted_items_reject_every_orb() {
        val corrupted = item(EnumRarity.RARE, corrupted = true)

        EnumCurrencyOrb.entries.forEach { orb ->
            val failed = runCatching { CurrencyApplier.apply(orb, corrupted, template()) }.isFailure
            assert(failed) { "$orb was applied to a corrupted item" }
        }
    }

    @Test
    fun rarity_gated_orbs_reject_the_wrong_rarity() {
        val wrongRarity = mapOf(
            EnumCurrencyOrb.ORB_OF_TRANSMUTATION to EnumRarity.RARE,
            EnumCurrencyOrb.ORB_OF_ALCHEMY to EnumRarity.RARE,
            EnumCurrencyOrb.ORB_OF_CHANCE to EnumRarity.RARE,
            EnumCurrencyOrb.ORB_OF_ALTERATION to EnumRarity.COMMON,
            EnumCurrencyOrb.ORB_OF_AUGMENTATION to EnumRarity.COMMON,
            EnumCurrencyOrb.REGAL_ORB to EnumRarity.COMMON,
            EnumCurrencyOrb.CHAOS_ORB to EnumRarity.COMMON,
            EnumCurrencyOrb.EXALTED_ORB to EnumRarity.COMMON,
        )

        wrongRarity.forEach { (orb, rarity) ->
            val failed = runCatching { CurrencyApplier.apply(orb, item(rarity), template()) }.isFailure
            assert(failed) { "$orb was applied to a $rarity item" }
        }
    }

    @Test
    fun mirror_creates_an_independent_corrupted_copy() {
        val source = item(EnumRarity.RARE).apply { equippedSlot = EnumEquipmentType.HELMET }
        val outcome = CurrencyApplier.apply(EnumCurrencyOrb.MIRROR_OF_KALANDRA, source, template())

        val copy = outcome.created
        assert(copy != null) { "Mirror created nothing" }
        assert(copy!!._id != source._id) { "Copy shares the source id" }
        assert(copy.version == 0L) { "Copy must be a fresh document" }
        assert(copy.corrupted) { "Mirrored copy must be corrupted" }
        assert(copy.equippedSlot == null) { "Copy must not arrive equipped" }
        assert(copy.params !== source.params) { "Copy shares the params list with the source" }
        assert(!source.corrupted) { "Mirror must not corrupt the source" }
    }

    @Test
    fun uniques_cannot_be_scoured() {
        val failed = runCatching {
            CurrencyApplier.apply(EnumCurrencyOrb.ORB_OF_SCOURING, item(EnumRarity.UNIQUE), template(EnumRarity.UNIQUE))
        }.isFailure
        assert(failed) { "A unique item was scoured" }
    }

    @Test
    fun equipment_price_follows_the_rarity_ladder() {
        val prices: List<Pair<EnumRarity, Equipment>> =
            listOf(EnumRarity.COMMON, EnumRarity.UNCOMMON, EnumRarity.RARE).map { it to template(it) }

        prices.zipWithNext { (lowerRarity, lower), (higherRarity, higher) ->
            assert(higher.price > lower.price) { "$higherRarity is not worth more than $lowerRarity" }
        }
    }
}

import application.enums.EnumAuctionLotKind
import application.enums.EnumAuctionLotStatus
import application.enums.EnumEquipmentType
import application.enums.EnumRarity
import com.mongodb.MongoClientSettings
import features.data.auction.AuctionLot
import features.data.auction.AuctionSearch
import features.data.character.Character
import features.data.equipment.equipment_data.Armor
import features.data.inventory.CharacterEquipment
import features.data.items.Items
import org.bson.BsonDocument
import org.bson.BsonValue
import org.bson.conversions.Bson
import org.junit.Test

/**
 * Аукцион: фильтр витрины и снимок предмета в лоте.
 * Mongo не нужна - фильтр разворачивается в документ на месте.
 */
class AuctionTest {

    private fun Bson.render(): BsonDocument =
        toBsonDocument(BsonDocument::class.java, MongoClientSettings.getDefaultCodecRegistry())

    private fun conditions(search: AuctionSearch): List<BsonDocument> =
        search.toFilter().render()
            .getArray("\$and")
            .map { it.asDocument() }

    /**
     * Значения всех условий по указанному полю. Границ у одного поля может быть
     * несколько, поэтому возвращается список, а не одно значение.
     */
    private fun conditionsOn(search: AuctionSearch, field: String): List<BsonValue> =
        conditions(search).filter { it.containsKey(field) }.map { it.getValue(field) }

    // ==================== Фильтр витрины ====================

    @Test
    fun the_showcase_never_shows_closed_lots() {
        // Даже пустой фильтр обязан отсечь проданные и снятые лоты
        val conditions = conditions(AuctionSearch())

        assert(conditions.size == 1) { "expected only the status condition, got $conditions" }
        assert(conditions.first().getString("status").value == EnumAuctionLotStatus.ACTIVE.name) {
            "got ${conditions.first()}"
        }
    }

    @Test
    fun enums_go_into_the_filter_as_strings() {
        // В Mongo перечисления лежат строками, поэтому и фильтр должен быть строковым
        val search = AuctionSearch(
            kind = EnumAuctionLotKind.EQUIPMENT,
            slot = EnumEquipmentType.HELMET,
            rarity = EnumRarity.UNIQUE
        )

        assert(conditionsOn(search, "kind").single().asString().value == "EQUIPMENT")
        assert(conditionsOn(search, "slot").single().asString().value == "HELMET")
        assert(conditionsOn(search, "rarity").single().asString().value == "UNIQUE")
    }

    @Test
    fun ranges_become_boundaries_and_not_equality() {
        val search = AuctionSearch(minItemLevel = 60, maxItemLevel = 80, maxPrice = 25)

        // Две границы одного поля - два отдельных условия внутри $and,
        // иначе одна затёрла бы другую
        val levels = conditionsOn(search, "itemLevel").map { it.asDocument() }
        assert(levels.size == 2) { "got $levels" }
        fun bound(name: String) = levels.singleOrNull { it.containsKey(name) }?.getInt32(name)?.value

        assert(bound("\$gte") == 60) { "no lower bound: $levels" }
        assert(bound("\$lte") == 80) { "no upper bound: $levels" }

        val price = conditionsOn(search, "price").single().asDocument()
        assert(price.getInt64("\$lte").value == 25L) { "got $price" }
    }

    @Test
    fun a_search_by_title_cannot_smuggle_a_regular_expression() {
        // Игрок присылает текст, а не шаблон: спецсимволы должны быть экранированы
        val pattern = conditionsOn(AuctionSearch(title = "Helm.*"), "title")
            .single()
            .asRegularExpression()

        assert(pattern.options.contains("i")) { "search must ignore case, got '${pattern.options}'" }
        assert(!pattern.pattern.endsWith(".*")) { "the pattern went in raw: ${pattern.pattern}" }
        assert(pattern.pattern.contains("Helm.*")) { "the text was lost: ${pattern.pattern}" }
    }

    @Test
    fun a_character_can_hide_its_own_lots_from_the_showcase() {
        val condition = conditionsOn(AuctionSearch(excludeSellerId = "me"), "sellerId")
            .single()
            .asDocument()

        assert(condition.getString("\$ne").value == "me") { "got $condition" }
    }

    // ==================== Лот ====================

    private val seller = Character(userId = "user", name = "Seller", level = 12, _id = "seller-id")

    private fun helm() = Armor(
        slot = EnumEquipmentType.HELMET,
        name = "Test Helm",
        rarity = EnumRarity.COMMON,
        itemLevel = 74
    )

    @Test
    fun a_listed_item_leaves_its_owner_behind() {
        val template = helm()
        val item = CharacterEquipment(
            characterId = seller._id,
            equipmentId = template._id,
            rarity = EnumRarity.RARE,
            equippedSlot = EnumEquipmentType.HELMET
        )

        val lot = AuctionLot.forEquipment(seller, item, template, priceOrbId = "orb", price = 50)

        // Пока лот на витрине, предмет ничей и уж точно не надет
        assert(lot.equipment!!.characterId.isEmpty()) { "got '${lot.equipment!!.characterId}'" }
        assert(lot.equipment!!.equippedSlot == null) { "got ${lot.equipment!!.equippedSlot}" }
    }

    @Test
    fun the_showcase_card_describes_the_instance_and_not_the_template() {
        val template = helm()
        // Редкость экземпляру подняли сферы, шаблон об этом не знает
        val item = CharacterEquipment(seller._id, template._id, rarity = EnumRarity.RARE)

        val lot = AuctionLot.forEquipment(seller, item, template, priceOrbId = "orb", price = 50)

        assert(lot.kind == EnumAuctionLotKind.EQUIPMENT) { "got ${lot.kind}" }
        assert(lot.title == "Test Helm") { "got ${lot.title}" }
        assert(lot.slot == EnumEquipmentType.HELMET) { "got ${lot.slot}" }
        assert(lot.rarity == EnumRarity.RARE) { "rarity must come from the instance, got ${lot.rarity}" }
        assert(lot.itemLevel == 74) { "got ${lot.itemLevel}" }
        assert(lot.amount == 1L) { "equipment is never stacked, got ${lot.amount}" }
        assert(lot.sellerName == "Seller") { "got ${lot.sellerName}" }
        assert(lot.isOnSale()) { "a fresh lot must be on sale" }
    }

    @Test
    fun a_stack_lot_keeps_its_amount_and_has_no_slot() {
        val orb = Items(name = "Chaos Orb", category = "CURRENCY", subCategory = "CHAOS_ORB", _id = "chaos")

        val lot = AuctionLot.forItem(seller, orb, amount = 30, priceOrbId = "divine", price = 2)

        assert(lot.kind == EnumAuctionLotKind.ITEM) { "got ${lot.kind}" }
        assert(lot.itemId == "chaos") { "got ${lot.itemId}" }
        assert(lot.amount == 30L) { "got ${lot.amount}" }
        assert(lot.equipment == null) { "a stack lot must carry no equipment" }
        assert(lot.slot == null && lot.rarity == null) { "a stack lot has no slot or rarity" }
        assert(lot.price == 2L && lot.priceOrbId == "divine") { "got ${lot.price} of ${lot.priceOrbId}" }
    }
}

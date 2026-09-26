package features.data.auction

import base.entity.VersionedEntity
import com.sperance.exileforge.rules.content.ItemTemplate
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.content.Slot
import com.sperance.exileforge.rules.roll.ItemInstance
import extensions.now
import features.data.hero.Hero
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
enum class LotKind { EQUIPMENT, ITEM }

@Serializable
enum class LotStatus { ACTIVE, SOLD, CANCELLED }

/**
 * Лот аукциона - отдельная коллекция `AuctionLot`. Пока лот на витрине, товар лежит в нём, а не у
 * продавца: копия вещи уходит из документа героя, стопка списывается из сумки. Цена - только в сферах
 * ([priceOrb] - код предмета категории CURRENCY). Поля витрины - снимок вещи на момент выставления,
 * чтобы фильтр работал одним запросом к Mongo без справочников; названий нет - клиент берёт текст по коду.
 */
@Serializable
data class AuctionLot(
    var sellerId: String,
    var sellerName: String = "",
    var kind: LotKind = LotKind.EQUIPMENT,
    /** Копия вещи, снятая с продавца; у стопок null. */
    var equipment: ItemInstance? = null,
    /** Код предмета стопки; у вещи пусто. */
    var item: String = "",
    var amount: Long = 1,
    var priceOrb: String = "",
    var price: Long = 0,
    var itemCode: String = "",
    var slot: Slot? = null,
    var rarity: Rarity? = null,
    var itemLevel: Int = 0,
    var status: LotStatus = LotStatus.ACTIVE,
    var buyerId: String? = null,
    var closedAt: LocalDateTime? = null,
    override var _id: String = ObjectId().toHexString(),
    override var version: Long = 0,
    override var deleted: Boolean = false,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override var updatedAt: LocalDateTime = LocalDateTime.now(),
) : VersionedEntity {

    fun isOnSale(): Boolean = status == LotStatus.ACTIVE

    companion object {
        fun forEquipment(seller: Hero, item: ItemInstance, template: ItemTemplate, priceOrb: String, price: Long): AuctionLot = AuctionLot(
            sellerId = seller._id, sellerName = seller.name, kind = LotKind.EQUIPMENT, equipment = item.copy(slot = null, socket = null),
            priceOrb = priceOrb, price = price, itemCode = template.code, slot = template.slot, rarity = item.rarity, itemLevel = template.level,
        )

        fun forItem(seller: Hero, code: String, amount: Long, priceOrb: String, price: Long): AuctionLot = AuctionLot(
            sellerId = seller._id, sellerName = seller.name, kind = LotKind.ITEM, item = code, amount = amount, priceOrb = priceOrb, price = price, itemCode = code,
        )
    }
}
